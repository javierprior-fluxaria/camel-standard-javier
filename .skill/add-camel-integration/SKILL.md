---
name: add-camel-integration
description: >-
  Guía paso a paso y runbook estándar para añadir nuevos módulos, rutas,
  modelos canónicos, mappers y adaptadores en la arquitectura Camel 4 + Spring Boot 3 de Fluxaria.
  Usar siempre que el usuario pida implementar un nuevo flujo de integración, canal inbound o adaptador outbound.
---

# Runbook de Desarrollo: Cómo Añadir una Nueva Integración en Apache Camel 4

Esta guía define el procedimiento obligatorio, secuencial y estandarizado para implementar cualquier nueva funcionalidad, canal de entrada o integración de salida en la arquitectura corporativa de Fluxaria.

---

## Principios Innegociables de Arquitectura

1. **Separación Estricta en 3 Capas (API-Led / Hexagonal):**
   - **`inbound/{canal}`**: Solo traduce de protocolo externo (REST/JSON, SOAP/XML, Kafka) a Modelo Canónico.
   - **`orchestration/`**: Orquesta el flujo puro usando únicamente el Modelo Canónico. No conoce DTOs de transporte.
   - **`outbound/{sistema}`**: Traduce del Modelo Canónico al contrato propio del sistema destino (ERP, BD, Kafka, APIs).
   - **Prohibición de atajos Inbound -> Outbound:** Un adaptador Inbound NUNCA debe invocar adaptadores de salida (ni topics de respuesta ni colas DLQ). Todo flujo de negocio o lote pasa obligatoriamente por la capa de orquestación.
2. **Inbound Asíncrono 100% Tipado (Cero Payloads en Crudo):**
   - Los adaptadores de brokers de mensajería (Kafka, JMS, RabbitMQ) deben definir sus DTOs de contrato en `inbound/{canal}/dto/`.
   - La deserialización (`.unmarshal()`) ocurre en el Inbound Adapter antes de delegar a orquestación. Está prohibido pasar `String` o árboles Jackson a la orquestación.
   - Si un mensaje entrante tiene un JSON corrupto que no se puede deserializar, Inbound captura la excepción y lo desvía a la DLQ de inmediato con cabecera `X-Failure-Reason`.
3. **Ubicación de Lógica de Proceso (`orchestration/service/` vs `domain/`):**
   - Las clases que contienen lógica de proceso o workflow con dependencias de Apache Camel (ej. `AggregationStrategy`, agregadores en memoria, ruteadores dinámicos) deben situarse en **`orchestration/service/`** como `@Component` de Spring.
   - El paquete `domain/` debe mantenerse estrictamente puro (cero imports de Camel o Spring).
   - La raíz de `orchestration/` debe mantenerse plana (`OrderProcessRoute.java`, `OrderBatchRoute.java`), evitando la proliferación de subdirectorios por cada caso de uso.
4. **Regla de Simetría en Mappers:**
   - Todo parsing, transformación o preparación de parámetros vive en un `@Component` Spring en `{inbound|outbound}/{subsistema}/mapper/`.
   - **Prohibido:** Lambdas `.process(exchange -> { ... })` con `ObjectMapper`, creación de `HashMap` manuales o mutaciones imperativas dentro del DSL de Camel.
5. **Dominio Puro y Agnóstico:**
   - `domain/model/` y `domain/service/` contienen Java 21 puro (Records/POJOs). Cero dependencias de Camel, Jackson, Spring o JDBC.
6. **Patrón Splitter con Aislamiento y Política DLQ ("Nada se pierde en silencio"):**
   - En procesamiento de lotes o colecciones, aislar cada elemento con `.stopOnException(false)` y bloques `doTry/doCatch` para que un pedido erróneo no cancele el lote.
   - Todo elemento fallido se desvía a `direct:outbound.kafka.dlq` preservando el payload original, timestamp (`X-Failed-At`) y el motivo exacto en `X-Failure-Reason`.
7. **Resiliencia y Errores Estandarizados:**
   - Toda ruta extiende de `BaseRouteBuilder` para heredar el manejo de errores RFC 7807 (`Problem Details`) y trazabilidad MDC (`correlationId`).

---

## Procedimiento Paso a Paso

```
[Paso 1: Persistencia]  --> schema.sql (tablas e índices con CREATE TABLE IF NOT EXISTS)
          │
[Paso 2: Dominio]       --> domain/model/ (Java 21 Records / POJOs puros)
          │
[Paso 3: Validación]    --> domain/service/ (Validator determinista en memoria + Unit Tests)
          │
[Paso 4: Inbound]       --> inbound/{canal}/ (dto/, mapper/ y *Route.java con X-Correlation-ID)
          │
[Paso 5: Proceso]       --> orchestration/{caso}/ (*ProcessRoute.java sobre Modelo Canónico)
          │
[Paso 6: Outbound]      --> outbound/{sistema}/ (dto/, mapper/ simétrico y *Route.java con resiliencia)
          │
[Paso 7: Configuración] --> application.yml (propiedades jerárquicas tipadas integration.outbound.*)
          │
[Paso 8: Verificación]  --> Unit Tests puros (Mappers/Validators) + E2E Tests (PowerShell/Postman)
```

---

### Paso 1: Persistencia y Esquema (`schema.sql`)
1. Si el nuevo flujo persiste datos en PostgreSQL, declarar las tablas e índices en `src/main/resources/schema.sql` con sintaxis idempotente (`CREATE TABLE IF NOT EXISTS`).
2. Mantener la tabla de auditoría de eventos (`order_events` o equivalente) con columna `correlation_id` para trazabilidad transversal.

---

### Paso 2: Modelo Canónico de Dominio (`domain/model/`)
1. Crear las entidades y value objects en `src/main/java/com/fluxaria/middleware/domain/model/`.
2. Utilizar Java 21 `record` para value objects inmutables y clases POJO puras para agregados raíz con lógica de transición de estado (ej. `markConfirmed()`).
3. Implementar métodos de fábrica estáticos (ej. `createNew(...)`).

```java
public class Order {
    private final String orderId;
    private OrderStatus status;
    // ...
    public void markAsConfirmedByErp() {
        this.status = OrderStatus.CONFIRMED;
        this.updatedAt = Instant.now();
    }
}
```

---

### Paso 3: Validación de Negocio Funcional (`domain/service/`)
1. Crear la clase `{Caso}BusinessValidator.java` anotada como `@Component`.
2. Implementar reglas deterministas en memoria (validación de esquemas, códigos ISO, importes > 0).
3. Si la validación falla, lanzar `BusinessValidationException` con la lista de campos erróneos (`invalidParams`).
4. **Escribir test unitario inmediato** en `src/test/java/.../domain/` con JUnit 5 puro (sin levantar Spring ni Camel).

---

### Paso 4: Adaptador de Entrada (`inbound/{canal}/`)
1. **Contratos DTO (`inbound/{canal}/dto/`):**
   - Definir los Records de entrada y respuesta con anotaciones Jackson si aplica.
2. **Mapper Inbound (`inbound/{canal}/mapper/`):**
   - Componente Spring `@Component` con métodos `toDomain(Dto request)` y `toResponseDto(Order domain)`.
3. **Ruta Inbound (`inbound/{canal}/{Canal}Route.java`):**
   - Extender `BaseRouteBuilder`.
   - Asignar `routeId("{capa}.{canal}.{accion}")`.
   - Inyectar `correlationIdProcessor` para propagar `X-Correlation-ID` en el MDC.
   - Invocar `.bean(mapper, "toDomain")` y saltar a la capa de orquestación con `.to("direct:orchestration...")`.

---

### Paso 5: Orquestador del Proceso (`orchestration/`)
1. Crear `{Caso}Route.java` en la raíz de `orchestration/` extendiendo `BaseRouteBuilder`.
2. **Flujo Unitario:** Conectar las etapas de negocio en orden secuencial usando el modelo canónico:
   ```java
   from(DIRECT_PROCESS)
       .routeId("orchestration.order.process")
       .bean(businessValidator, "validate")
       .to("direct:outbound.country.enrich")
       .to("direct:outbound.currency.convert")
       .to("direct:outbound.database.save-initial")
       .to("direct:outbound.erp.submit")
       .to("direct:outbound.database.save-final")
       .to("direct:outbound.kafka.publish");
   ```
3. **Flujos Asíncronos / Lotes (EIP Splitter + Aggregator):**
   - Mantener la ruta en la raíz: `orchestration/OrderBatchRoute.java` (sin subdirectorios anidados).
   - Recibir la colección de dominio (`List<Order>`) deserializada por el adaptador Inbound.
   - Situar la clase agregadora (`AggregationStrategy`) en **`orchestration/service/`**.
   - Invocar el orquestador unitario dentro del Splitter: `.to(OrderProcessRoute.DIRECT_PROCESS)`.
   - Aislar errores de cada elemento con `.stopOnException(false)` y desviar a DLQ (`direct:outbound.kafka.dlq`).
   - Al completar la agregación, publicar el resumen hacia el adaptador de salida (`direct:outbound.kafka.batch-summary`).

---

### Paso 6: Adaptadores de Salida (`outbound/{sistema}/`)
Para cada sistema externo (HTTP, Base de Datos, Kafka):
1. **DTOs Propietarios (`outbound/{sistema}/dto/`):**
   - Definir los Records según el contrato del sistema destino.
2. **Mapper Simétrico (`outbound/{sistema}/mapper/`):**
   - Crear el `@Component` que transforma `Order` -> DTO externo o Map de parámetros SQL.
   - Para bases de datos: métodos que devuelven `Map<String, Object>` para `sql:INSERT` / `sql:UPDATE`.
   - Para Kafka: métodos que devuelven el Record de evento.
3. **Ruta Outbound (`outbound/{sistema}/{Sistema}OutboundRoute.java`):**
   - Configurar timeouts de conexión y respuesta.
   - Envolver llamadas inestables con `.circuitBreaker().resilience4jConfiguration()`.
   - Definir políticas de reintento (`onException`) solo para fallos transitorios de red o 500/502.
   - Restaurar el body usando DSL nativo de Camel: `.setBody(exchangeProperty("..."))`.

---

### Paso 7: Configuración Externa (`application.yml`)
1. Parametrizar URLs, timeouts, número de reintentos y umbrales de circuit breaker bajo el prefijo corporativo `integration.outbound.{sistema}`.
2. Usar variables de entorno con valores por defecto: `${ERP_BASE_URL:http://localhost:8080/...}`.

---

### Paso 8: Batería de Pruebas y Verificación
1. **Tests Unitarios de Mappers (`src/test/.../mapper/`):**
   - Verificar transformaciones canónicas de ida y vuelta con JUnit 5 puro (0 milisegundos).
2. **Tests End-to-End (`test-orders.ps1` o Postman):**
   - Ejecutar la suite cubriendo:
     - Happy Path (`201 Created`).
     - Validación Funcional (`400 Bad Request` en formato RFC 7807).
     - Idempotencia y Conflicto (`409 Conflict`).
     - Timeouts de Red (`500 Internal Server Error` controlado).
     - Recuperación de fallos transitorios tras reintentos (`201 Created`).
     - Apertura de Circuit Breaker ante caída permanente.
