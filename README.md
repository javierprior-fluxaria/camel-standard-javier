# Fluxaria Middleware: Apache Camel 4 Standard Architecture Blueprint

Este repositorio implementa el **Blueprint de Arquitectura Estándar Corporativa** para el desarrollo de integraciones middleware utilizando **Apache Camel 4.x**, **Spring Boot 3.3.x** y **Java 21 LTS**.

El proyecto sirve como referencia y base de código de producción, demostrando la arquitectura mediante el caso de uso del **Procesamiento de Pedido Internacional**.

---

## 🏛️ Principios y Decisiones Arquitecturales

El proyecto sigue una arquitectura **Hexagonal (Ports and Adapters)** y **API-Led Connectivity**:

1. **Separación en 3 Capas de Integración:**
   - **`inbound/{canal}` (Experience API):** Traduce de protocolos de transporte externos (REST, SOAP, colas) al Modelo Canónico. Inyecta identificador de correlación.
   - **`orchestration/{caso}` (Process API):** Coordina la lógica de negocio pura operando exclusivamente sobre el Modelo Canónico.
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
│   └── test.md                         # Guía de pruebas con payloads para Postman
├── src/main/java/com/fluxaria/middleware/
│   ├── Application.java                # Spring Boot + Apache Camel Entrypoint
│   ├── domain/                         # Núcleo puro de negocio
│   │   ├── model/                      # Order, OrderItem, CountryDetails, OrderStatus
│   │   └── service/                    # OrderBusinessValidator (reglas funcionales en memoria)
│   ├── inbound/rest/                   # Adaptador REST (/api/v1/orders)
│   │   ├── dto/                        # OrderRequestDto, OrderResponseDto
│   │   ├── mapper/                     # OrderInboundMapper
│   │   └── OrderRestRoute.java         # Expone endpoint REST con Camel Servlet
│   ├── orchestration/                  # Capa de proceso
│   │   └── OrderProcessRoute.java      # Orquestador del pedido internacional
│   ├── outbound/                       # Adaptadores de salida
│   │   ├── country/                    # Enriquecimiento RestCountries (mapper + route)
│   │   ├── currency/                   # Conversión de divisa a EUR (mapper + route)
│   │   ├── database/                   # Persistencia PostgreSQL (OrderDatabaseMapper + OrderDatabaseRoute)
│   │   ├── erp/                        # Integración Legacy ERP con Circuit Breaker y Retries
│   │   └── kafka/                      # Publicación en Kafka topic orders.processed (OrderKafkaMapper + Route)
│   ├── backend/mock/                   # Simulador desacoplado del ERP Legacy
│   └── shared/                         # Cross-cutting (BaseRouteBuilder, MDC Correlation, ErrorResponse)
├── src/main/resources/
│   ├── application.yml                 # Configuración tipada externa
│   └── schema.sql                      # DDL de PostgreSQL (orders, order_events)
├── docker-compose.yml                  # Infraestructura local: PostgreSQL 16 y Kafka KRaft
└── test-orders.ps1                     # Suite automatizada de pruebas E2E (PowerShell 5.1 y 7+)
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
*Ejecutará los 12 tests unitarios de validación y mappers en milisegundos.*

### 3. Iniciar la Aplicación Middleware
```bash
mvn spring-boot:run
```
La aplicación arrancará en el puerto `8080` con el contexto servlet de Camel en `/api/*`.

### 4. Comprobaciones de Salud (Actuator)
- **Health Check:** `http://localhost:8080/actuator/health`
- **Métricas Prometheus:** `http://localhost:8080/actuator/prometheus`

---

## 🧪 Ejecución de Pruebas

### Opción A: Batería End-to-End Automática (Recomendado)
Abre una terminal de PowerShell y ejecuta:
```powershell
.\test-orders.ps1
```
El script ejecutará automáticamente los 6 escenarios de negocio y resiliencia:
- **Test 1:** Pedido Válido (`ES`, `EUR`) -> `201 Created`
- **Test 2:** Validación de Negocio Fallida (`XX`, `INVALID`, cantidades < 0) -> `400 Bad Request` (RFC 7807)
- **Test 3:** Idempotencia ERP / Conflicto Duplicado -> `409 Conflict` (RFC 7807)
- **Test 4:** ERP Lento (5s delay > 3s timeout) -> `500 Internal Server Error` controlado
- **Test 5:** ERP 500 Intermitente -> Reintentos con Backoff Exponencial y posterior `201 Created`
- **Test 6:** ERP 503 Permanente -> Apertura de Circuit Breaker y fallback controlado

### Opción B: Pruebas Manuales con Postman o cURL
Consulta la guía completa con todos los payloads JSON y respuestas esperadas en:
👉 [docs/test.md](docs/test.md)

---

## 📊 Observabilidad y Trazabilidad

- **`X-Correlation-ID`:** Si el cliente envía la cabecera `X-Correlation-ID`, se preserva; si no, el middleware genera un UUID automáticamente.
- **MDC (Mapped Diagnostic Context):** El `correlationId` se inyecta en cada hilo de ejecución de SLF4J, garantizando que todos los logs contengan el ID de correlación.
- **Auditoría:** Cada transición de estado del pedido se audita en la tabla `order_events` vinculada al `correlation_id`.
- **Kafka:** Todo evento publicado en `orders.processed` incluye la cabecera `X-Correlation-ID` y clave de particionamiento por `orderId`.
