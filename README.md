# Fluxaria Middleware: Apache Camel 4 Standard Architecture Blueprint

Este repositorio implementa el **Blueprint de Arquitectura Estándar Corporativa** para el desarrollo de integraciones middleware utilizando **Apache Camel 4.x**, **Spring Boot 3.3.x** y **Java 21 LTS**.

El proyecto sirve como referencia y base de código de producción, demostrando la arquitectura mediante el caso de uso del **Procesamiento de Pedido Internacional**.

---

## 🏛️ Principios y Decisiones Arquitecturales

El proyecto sigue una arquitectura **Hexagonal (Ports and Adapters)** y **API-Led Connectivity**:

1. **Separación en 3 Capas de Integración:**
   - **`inbound/{canal}` (Experience API):** Traduce de protocolos de transporte externos (REST, SOAP, colas) al Modelo Canónico. Inyecta identificador de correlación.
   - **`orchestration/` (Process API):** Coordina la lógica de negocio pura operando exclusivamente sobre el Modelo Canónico.
   - **`outbound/{sistema}` (System API):** Adaptadores de salida hacia ERPs, bases de datos (PostgreSQL), mensajería (Kafka) o APIs REST externas.
2. **Simetría Estricta de Mappers:**
   - Todo parsing, transformación o preparación de parámetros SQL vive exclusivamente en clases `@Component` en `{inbound|outbound}/{subsistema}/mapper/`.
   - **Prohibición de `.process()` imperativos:** Las rutas de Camel son 100% declarativas (`.bean()`, `.marshal()`, `.to()`, `.setBody()`).
3. **Dominio Canónico Agnóstico:**
   - `domain/model/` y `domain/service/` están escritos en Java 21 puro (Records y POJOs), sin anotaciones de frameworks externos (cero Camel, Jackson o Spring).
4. **Manejo Estandarizado de Errores (RFC 7807) y Resiliencia:**
   - Respuestas de error estructuradas según RFC 7807 (`application/problem+json`).
   - Circuit Breaker con **Resilience4j** y políticas de reintento con **Backoff Exponencial** en llamadas a sistemas externos.

Para más detalle, consulta el catálogo de decisiones:
- 📖 [Registro de Decisiones Arquitecturales (ADRs)](docs/adr/README.md)
- 📖 [Documento Completo de Decisiones de Arquitectura](docs/architecture-decisions.md)
- 📖 [Runbook / Skill de Desarrollo: Cómo Añadir una Integración](.skill/add-camel-integration/SKILL.md)

---

## 📂 Estructura del Código

```text
camel-standard-architecture/
├── .skill/add-camel-integration/       # Skill a nivel de proyecto con el runbook del desarrollador
├── docs/                               # Documentación, ADRs y contratos de prueba
│   ├── adr/                            # Architecture Decision Records (ADR-001 al ADR-007)
│   ├── architecture-decisions.md       # Análisis comparativo y alternativas descartadas
│   └── test.md                         # Guía de pruebas con payloads para Postman y Kafka
├── src/main/java/com/fluxaria/middleware/
│   ├── Application.java                # Spring Boot + Apache Camel Entrypoint
│   ├── domain/                         # Núcleo puro de negocio (Java 21 agnóstico a frameworks)
│   │   ├── model/                      # Order, OrderItem, CountryDetails, OrderStatus, BatchSummary
│   │   └── service/                    # OrderBusinessValidator (reglas funcionales en memoria)
│   ├── inbound/                        # Capa Experience (Adaptadores de entrada)
│   │   ├── kafka/                      # Consumidor asíncrono de lotes (topic orders.batch.in)
│   │   │   ├── dto/                    # OrderBatchItemDto, OrderBatchItemProductDto
│   │   │   ├── mapper/                 # OrderBatchInboundMapper (Kafka DTO -> Order)
│   │   │   └── OrderBatchKafkaConsumerRoute.java
│   │   └── rest/                       # API REST sincrona (/api/v1/orders)
│   │       ├── dto/                    # OrderRequestDto, OrderResponseDto, OrderItemDto
│   │       ├── mapper/                 # OrderInboundMapper (REST DTO -> Order)
│   │       └── OrderRestRoute.java     # Endpoint REST con Camel Servlet
│   ├── orchestration/                  # Capa Process (Orquestación agnóstica a transporte)
│   │   ├── OrderProcessRoute.java      # Orquestador del pedido internacional individual (Flujo A)
│   │   ├── OrderBatchRoute.java        # Orquestador de lotes (Flujo B: Splitter + Aggregator + DLQ)
│   │   └── service/                    # Lógica de proceso en memoria
│   │       └── BatchAggregationService.java # Agregador EIP del balance del lote
│   ├── outbound/                       # Capa System (Adaptadores de salida hacia sistemas externos)
│   │   ├── country/                    # Enriquecimiento RestCountries (mapper + route)
│   │   ├── currency/                   # Conversión de divisa a EUR (mapper + route)
│   │   ├── database/                   # Persistencia PostgreSQL (OrderDatabaseMapper + OrderDatabaseRoute)
│   │   ├── erp/                        # Integración Legacy ERP con Circuit Breaker y Retries
│   │   └── kafka/                      # Publicación en Kafka (OrderKafkaMapper + Route)
│   │       ├── dto/                    # OrderKafkaEventDto, OrderBatchSummaryEventDto
│   │       ├── mapper/                 # OrderKafkaMapper
│   │       └── OrderKafkaProducerRoute.java # Publica en orders.processed, orders.batch.summary y orders.dlq
│   ├── backend/mock/                   # Simulador desacoplado del ERP Legacy
│   └── shared/                         # Cross-cutting (BaseRouteBuilder, MDC Correlation, ErrorResponse)
├── src/main/resources/
│   ├── application.yml                 # Configuración tipada externa (DB, Kafka, Resiliencia)
│   └── schema.sql                      # DDL de PostgreSQL (orders, order_events)
├── docker-compose.yml                  # Infraestructura local: PostgreSQL 16 y Kafka KRaft
├── test-orders.ps1                     # Suite automatizada Flujo A (REST síncrono)
└── test-batch.ps1                      # Suite automatizada Flujo B (Kafka asíncrono por lotes)
```

---

## 🚀 Requisitos Previos

- **Java JDK 21 LTS**
- **Apache Maven 3.9+**
- **Docker Desktop** (con Docker Compose v2+)

---

## 🛠️ Puesta en Marcha (Paso a Paso)

### 1. Iniciar la Infraestructura de Soporte (Docker)
En la raíz del proyecto, ejecuta:
```bash
docker compose up -d
```
Esto levantará:
- **PostgreSQL 16** en el puerto `5432` (Base de datos: `orders_db`, Usuario: `fluxaria`, Password: `fluxaria_password`).
- **Apache Kafka (KRaft)** en el puerto `9092`.

Verifica que los contenedores estén activos:
```bash
docker ps
```

### 2. Compilar el Proyecto y Ejecutar Tests Unitarios
```bash
mvn clean test
```
*Ejecutará los 25 tests automáticos (unitarios, mappers, servicios y el Quality Gate de ArchUnit).*

### 3. Iniciar la Aplicación Middleware

#### Modo Local (Host JVM):
```bash
mvn spring-boot:run
```
La aplicación arrancará en el puerto `8080` con el contexto servlet de Camel en `/api/*`.

#### Modo Contenedor Completo (Docker Compose App):
Si prefieres ejecutar todo el stack (PostgreSQL + Kafka + Camel Middleware) en contenedores Docker:
```bash
docker compose -f docker-compose.app.yml up -d --build
```

### 4. Pruebas de Integración Herméticas (CI / Testcontainers)
Ejecuta la suite de integración que levanta instancias efímeras de PostgreSQL y Kafka sin dependencias del entorno:
```bash
mvn verify
```

---

## 🧪 Ejecución de Pruebas

### Opción A: Batería End-to-End Automática (Recomendado)
Abre una terminal de PowerShell y ejecuta:
- `.\test-orders.ps1` (6 escenarios Flujo A)
- `.\test-batch.ps1` (Lote asíncrono con DLQ Flujo B)

### Opción B: Pruebas Manuales con Postman o cURL
Consulta la guía completa con todos los payloads JSON y respuestas esperadas en:
👉 [docs/test.md](docs/test.md)

---

## 🚀 Integración Continua y Despliegue (CI/CD)

El proyecto incluye configuraciones listas para producción para plataformas de CI/CD:

- **GitHub Actions:** [`.github/workflows/ci.yml`](.github/workflows/ci.yml)
  - Quality Gate: Compilación Java 21, Tests Unitarios y Validación de Reglas Hexagonales con **ArchUnit**.
  - Pruebas Herméticas con **Testcontainers**.
  - Construcción y etiquetado de imagen Docker con hash de commit y `latest`.
- **Azure DevOps:** [`deploy/azure-pipelines.yml`](deploy/azure-pipelines.yml)
  - Pipeline multi-stage para Azure Pipelines con cache de dependencias Maven y publicación de resultados JUnit.
- **Dockerfile Multi-Stage:** [`deploy/Dockerfile`](deploy/Dockerfile)
  - Imagen mínima y segura basada en `eclipse-temurin:21-jre-alpine`.
  - Usuario de ejecución no privilegiado (`appuser:appgroup`).
  - Flags de contenedor de alto rendimiento (`-XX:+UseZGC -XX:+ZGenerational`).

---

## 📊 Observabilidad y Trazabilidad

- **`X-Correlation-ID`:** Si el cliente envía la cabecera `X-Correlation-ID`, se preserva; si no, el middleware genera un UUID automáticamente.
- **MDC (Mapped Diagnostic Context):** El `correlationId` se inyecta en cada hilo de ejecución de SLF4J, garantizando que todos los logs contengan `[corrId:...]`.
- **Auditoría:** Cada transición de estado del pedido se audita en la tabla `order_events` vinculada al `correlation_id`.
- **Kafka:** Todo evento publicado en `orders.processed`, `orders.batch.summary` y `orders.dlq` incluye la cabecera `X-Correlation-ID`.

