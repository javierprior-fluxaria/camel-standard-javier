# 1. Runtime y Versiones (Java, Camel, Framework)

### Decisión Estándar
* **Java:** **OpenJDK 21 LTS**
* **Apache Camel:** **4.8.x o 4.14.x LTS (serie Camel 4.x)**
* **Framework:** **Spring Boot 3.3.x+**

### ¿Por qué esta combinación?
1. **Java 21 LTS:** 
   * **Soporte a largo plazo:** Es el baseline obligatorio de Spring Boot 3 y Camel 4. 
   * **Virtual Threads (Project Loom):** Permite concurrencia masiva en llamadas I/O bloqueantes sin la complejidad del código reactivo (`CompletableFuture` o WebFlux).
   * **Language features modernos:** *Pattern matching* para `switch`, *Records* (ideales para DTOs inmutables) y *Sequenced Collections*, lo que reduce drásticamente el código *boilerplate*.
2. **Apache Camel 4.x LTS:**
   * Abandono total de `javax.*` en favor de `jakarta.*`.
   * Optimización profunda del core: menor asignación de memoria por `Exchange` y soporte nativo para Virtual Threads en thread pools y componentes (`camel-http`, `camel-kafka`, etc.).
   * Versión con soporte empresarial activo frente al fin de vida de Camel 3.x.
3. **Spring Boot 3.3.x:**
   * Proporciona la capa operacional empresarial out-of-the-box: métricas Prometheus (`micrometer-registry-prometheus`), comprobaciones de salud para Kubernetes (`Actuator` con `/health/liveness` y `/health/readiness`), y gestión de configuración jerárquica (`application.yml` multi-perfil).
   * Ecosistema unificado: cualquier desarrollador Java junior o senior es productivo desde el día 1 en inyección de dependencias, testing y perfiles.

---

### Opciones Descartadas y Por Qué

#### A. Descartado: Camel Quarkus
* **Qué prometía:** Arranque ultra-rápido (<50 ms en nativo con GraalVM) y menor huella de memoria RAM (~50-80 MB).
* **Por qué se descartó:**
  1. **Fricción con librerías legacy de clientes:** En integraciones middleware es habitual tener que integrar SDKs de terceros (drivers JDBC antiguos, clientes de SAP JCo, librerías SOAP corporativas firmadas). Si una librería usa reflexión dinámica no prevista por Quarkus, la compilación en nativo revienta en CI/CD y obliga a escribir extensiones custom o ficheros de configuración de reflexión incomprensibles.
  2. **Curva de aprendizaje del equipo:** El modelo de inyección ArC (CDI en build-time) y su suite de tests (`@QuarkusTest`, mocks limitados) genera fricción a desarrolladores formados en Spring.
  3. **Innecesario para el caso de uso:** En microservicios de middleware que corren 24/7 en Kubernetes, un arranque de 4 segundos y un consumo de 250 MB son perfectamente asumibles. No estamos haciendo *Scale-to-Zero* (Serverless/AWS Lambda).

#### B. Descartado: Camel Main Standalone (Vanilla Java)
* **Qué prometía:** Cero dependencias de frameworks externos, arranque rápido y control total del binario.
* **Por qué se descartó:**
  1. **Síndrome del "framework reinventado":** Al no tener Spring ni Quarkus, el equipo acaba creando su propio framework casero para leer ficheros YAML, inyectar singletons, exponer endpoints de métricas y health checks, etc. Cada proyecto acaba teniendo su propio runner no documentado.
  2. **Observabilidad artesanal:** Hay que configurar y levantar a mano servlets embebidos para exponer endpoints de `/health` o `/metrics` a Prometheus.

#### C. Descartado: Java 17 LTS / Java 11
* **Por qué se descartó:** Java 11 está fuera de soporte y es incompatible con Camel 4. Java 17 es compatible, pero renunciar a Java 21 implica perder los Virtual Threads nativos y las mejoras de rendimiento del garbage collector ZGC generacional, dejando al middleware obsoleto desde su nacimiento.

---

# 2. Estructura del Proyecto: Módulos, Paquetes y Convenciones

### Decisión Estándar: Proyecto Mono-Módulo con Arquitectura Hexagonal / API-Led en Paquetes

```
middleware-canonical-blueprint/
├── pom.xml
├── src/main/java/com/desigual/middleware/
│   ├── Application.java                      # SpringBootApplication + CamelMain
│   ├── shared/                               # Cross-cutting: Errores RFC 7807, Correlación MDC, Utils
│   ├── domain/                               # Núcleo Puro agnóstico a transporte
│   │   ├── model/                            # Entidades canónicas (Records / POJOs puros)
│   │   └── service/                          # Validaciones funcionales puras
│   ├── inbound/                              # Capa Experience (Rest, Soap, Kafka Consumer)
│   │   └── {canal}/                          # dto/, mapper/, routes...
│   ├── orchestration/                        # Capa Process (Rutas de negocio agnósticas)
│   │   └── {caso}/                           # Rutas direct/seda que unen inbound con outbound
│   └── outbound/                             # Capa System (ERP, CRM, BD, APIs terceras)
│       └── {sistema}/                        # dto/ propietario, mapper/, routes HTTP/JMS/RFC...
└── src/main/resources/
    ├── application.yml                       # Configuración externa tipada (@ConfigurationProperties)
    └── log4j2-spring.xml                     # Trazabilidad con %X{correlationId}
```

### Convenciones de Nombres Obligatorias

1. **Rutas (Route ID):**
   * Formato: `{capa}.{canal/sistema}.{acción}` en kebab-case o dot-notation clara.
   * *Ejemplos:* `inbound.rest.receive-order`, `orchestration.order.process-international`, `outbound.erp.create-order`.
   * **Regla estricta:** Toda ruta **debe** tener un `routeId(...)` explícito. Se prohíbe dejar que Camel genere IDs autoincrementales (`route1`, `route2`) porque hace imposible leer logs y métricas de Actuator.

2. **Endpoints de transporte interno:**
   * Utilizar **`direct:{routeId}`** para llamadas síncronas en el mismo hilo.
   * Utilizar **`seda:{routeId}?size=X&concurrentConsumers=Y`** para desacoplo asíncrono o búfer en memoria.

3. **Beans y Componentes:**
   * En camelCase, sufijo de rol claro: `OrderBusinessValidator`, `LegacyErpMapper`, `CorrelationIdProcessor`.
   * Registrados como `@Component` de Spring.

4. **Propiedades (`application.yml`):**
   * Agrupadas jerárquicamente por subsistema con prefijo corporativo:
     ```yaml
     integration:
       order-service:
         timeout-ms: 5000
       outbound:
         erp:
           base-url: "https://erp.internal/api"
           connect-timeout: 3000
           read-timeout: 10000
           max-retries: 3
     ```
   * Enlazadas en Java mediante clases `@ConfigurationProperties(prefix = "integration.outbound.erp")`. **Prohibido** llenar el código de `@Value("${...")` dispersos.

---

### Opciones Descartadas y Por Qué

#### A. Descartado: Multi-módulo Maven (`-core`, `-inbound`, `-outbound`, `-api`)
* **Qué prometía:** Aislamiento físico de dependencias en tiempo de compilación.
* **Por qué se descartó:** Para el tamaño típico de un microservicio de integración (un bounded context de negocio), el multi-módulo introduce una sobrecarga brutal de mantenimiento de POMs, dependencias circulares y complejidad en el CI/CD. Un único módulo bien paquetizado mediante visibilidad de paquetes (`package-private`) o validación con ArchUnit aporta el mismo orden sin la pesadez de 6 módulos Maven.

#### B. Descartado: Estructura por tipos de fichero (`routes/`, `mappers/`, `processors/`, `models/`)
* **Qué prometía:** El típico proyecto donde todas las rutas están juntas en una carpeta y todos los modelos en otra.
* **Por qué se descartó:** Viola el principio de alta cohesión. Cuando hay que tocar la integración del ERP, el desarrollador tiene que abrir 5 carpetas distintas (`routes/ErpRoute.java`, `mappers/ErpMapper.java`, `models/ErpDto.java`, `processors/ErpProcessor.java`). Organizar por subsistema (`outbound/erp/`) agrupa todo lo que cambia junto en el mismo lugar.

---

# 3. Organización de Rutas: Cómo partir la integración y evitar la "Ruta Monstruo"

### El Principio de Responsabilidad Única en Rutas (Single Responsibility Route)
Una integración no es un script procedural de arriba a abajo. Se divide estrictamente en 3 niveles (estilo API-Led Connectivity):

```
[Inbound Route]        --> Decodifica HTTP/REST -> Extrae Headers -> Mapea a Modelo Canónico
     │  (direct:)
     ▼
[Orchestration Route]  --> Valida Negocio -> Aplica Reglas/Filtros/Content-Based Router
     │  (direct:)
     ▼
[Outbound Route]       --> Mapea de Canónico a DTO ERP -> Llama a Red (HTTP/JMS) -> Parsea Respuesta
```

### Reglas de Diseño Anti-Monstruo
1. **Límite de líneas:** **Máximo 50-80 líneas de código Java por clase `RouteBuilder`**. Si una ruta pasa de ahí, es un code smell inmediato de que está haciendo cosas que no le corresponden.
2. **Prohibida la lógica de negocio dentro del DSL:**
   * El DSL de Camel sirve para **orquestar y enrutar**, no para computar.
   * **Antipatrón prohibido:** Meter bloques gigantes `.process(exchange -> { for(...) { if(...) { ... } } })`.
   * **Solución estándar:** Invocar Beans dedicados probados con JUnit: `.bean(OrderBusinessValidator.class, "validate")`.
3. **Puntos de unión transparentes:**
   * Las rutas se comunican entre sí mediante `direct:{nombre-ruta-destino}`.
   * El paso entre capas se hace **siempre** enviando como `Body` la **Entidad Canónica del Dominio**. Ninguna ruta de orquestación conoce el DTO JSON del cliente ni el DTO XML del ERP.

---

### Opciones Descartadas y Por Qué

#### A. Descartado: La "Ruta Monolítica" (Pipeline Todo-en-Uno)
* **Qué prometía:** Facilidad aparente de lectura al inicio: `from("rest:post").process(...).to("http://erp").process(...).to("mock:end")`.
* **Por qué se descartó:** Es la causa número 1 del código ininteligible y del legado de TIBCO en Camel. Cuando falla un error en la línea 400, no se sabe si falló la validación, el ERP o el cliente. Además, es imposible reutilizar la llamada al ERP desde otro canal (por ejemplo, si mañana entra el mismo pedido por Kafka).

#### B. Descartado: Reutilización ciega con subrutas genéricas ("Utility Routes")
* **Qué prometía:** Crear una ruta genérica `direct:generic-http-client` a la que todo el mundo llama pasando URLs por cabeceras.
* **Por qué se descartó:** Oculta el contrato de transporte, dispersa el control de errores (un timeout del ERP no se gestiona igual que un timeout del CRM) y dificulta el tracing. Cada sistema externo debe tener su propia ruta outbound con sus propios timeouts y circuit breakers configurados.

---

# 4. Modelo de Datos y Transformación: Mapeos, DTOs y DSL

### Decisión Estándar
* **Modelo Canónico Puro:** Clases de dominio inmutables (Java `record` o POJOs limpios) sin anotaciones de Jackson (`@JsonProperty`), ni de JAXB, ni de bases de datos.
* **DTOs de Frontera:** Clases específicas por canal/sistema con anotaciones de transporte (ej. `OrderRequestDto`, `LegacyErpOrderDto`).
* **Mapeo:** **MapStruct** o **Java Mappers puros basados en Beans**.
* **DSL:** **Java DSL** tipado al 100%.

### ¿Por qué MapStruct / Mappers Java puros frente a mapas o expresiones?
1. **Seguridad en tiempo de compilación:** Si el ERP cambia el nombre de un campo (`taxAmount` por `vat`), el proyecto **no compila**. En un mapa dinámico o un script JSONata/Groovy, el fallo se descubre en producción.
2. **Rendimiento absoluto:** MapStruct genera código Java directo (getters y setters). Tiene cero coste de reflexión en runtime y cero overhead de parseo.
3. **Mappers Java como Beans de Camel:**
   ```java
   // En la ruta:
   .to("direct:canonical-to-erp")
   .bean(LegacyErpMapper.class, "toLegacyDto")
   .to("http://erp-host/orders")
   ```

### Simetría Inbound / Outbound: ¿Dónde vive el Parsing y la Adaptación?
Para garantizar que cualquier desarrollador entienda inmediatamente dónde colocar cada pieza, se establece una **simetría estricta** entre la entrada (`inbound/`) y la salida (`outbound/`):

1. **`dto/` (Contratos de Frontera):** Representa cómo habla el sistema externo (request/response en JSON, eventos en Kafka, XML, etc.). Ejemplos: `OrderRequestDto`, `LegacyErpOrderDto`, `OrderKafkaEventDto`.
2. **`mapper/` (Traductores Explícitos):**
   * Convierte entre el formato técnico externo (o JSON de respuesta de una API como RestCountries/Frankfurter, mapas de parámetros SQL para `camel-sql`, o eventos de Kafka) y el **Modelo Canónico de Dominio**.
   * **Regla estricta:** Todo parsing, extracción de atributos de respuestas externas o preparación de payloads vive en un `@Component` en `mapper/`:
     - Base de Datos: `OrderDatabaseMapper` (prepara `Map<String, Object>` para `sql:INSERT` / `sql:UPDATE`).
     - Kafka: `OrderKafkaMapper` (transforma `Order` a `OrderKafkaEventDto`).
     - Legacy ERP: `LegacyErpMapper` (transforma `Order` a `LegacyErpOrderDto` y gestiona la transición de estado al confirmarse).
   * **Prohibición expresa:** Jamás escribir bloques `.process(...)` con lambdas imperativas para serializar JSON a mano (`ObjectMapper`), instanciar `HashMap` para JDBC o mutar entidades en las rutas.
3. **`domain/` (Dominio Puro y Cálculos Funcionales):**
   * El Dominio nunca conoce contratos ni estructuras de APIs externas.
   * Las operaciones matemáticas deterministas (como `order.applyExchangeRate(rate)`) y transiciones de estado (`order.markAsConfirmedByErp()`) residen en métodos de las entidades o en `domain/service/`.
4. **`*Route.java`:** La clase Camel se limita a orquestar el transporte (HTTP, JDBC, Kafka), invocar al mapper con `.bean(...)`, serializar con `.marshal().json()`, restaurar bodies con `.setBody(exchangeProperty(...))` y gestionar errores de red. Las rutas son 100% declarativas.

---

### ¿Por qué Java DSL para las Rutas?
* Autocompletado del IDE, refactorización segura con una tecla (`F6` / `Shift+F6`), detección de errores tipográficos en nombres de componentes en compilación y soporte completo de depuración paso a paso (breakpoints).

---

### Opciones Descartadas y Por Qué

#### A. Descartado: Modelo Genérico basado en `Map<String, Object>` o `JsonNode` (Sin tipos)
* **Qué prometía:** Flexibilidad total para pasar cualquier payload de un lado a otro sin crear clases.
* **Por qué se descartó:** El mayor antipatrón heredado de arquitecturas legacy. Convierte el código en un campo de minas de `ClassCastException`, `NullPointerException` y accesos tipo `((Map)body.get("header")).get("id")`. Nadie sabe qué datos viajan por la ruta sin poner un breakpoint o leer trazas.

#### B. Descartado: Transformaciones en DSL con Camel AtlasMap o Camel Dozer
* **Qué prometía:** Mapeadores visuales o declarativos basados en XML/JSON.
* **Por qué se descartó:** AtlasMap está discontinuado/deprecado en el ecosistema Camel moderno. Dozer está abandonado desde hace años y depende de reflexión lenta y propensa a fallos de carga de clases.

#### C. Descartado: Rutas en YAML DSL o XML DSL
* **Qué prometía:** Separar el código de la configuración (típico de Camel K o perfiles low-code).
* **Por qué se descartó:**
  * Pierde la potencia de tipado fuerte de Java.
  * La navegación y el *go to definition* en los IDEs es deficiente.
  * Manejar lógica condicional compleja o llamadas a servicios en YAML se convierte en un infierno de indentaciones y expresiones embebidas en Simple Language difíciles de probar con tests unitarios.

---

# 5. Errores, Reintentos, Resiliencia y Contratos de Salida

### Decisión Estándar
* **Contrato de Error HTTP:** Estándar internacional **RFC 7807 (Problem Details for HTTP APIs)** en formato JSON.
* **Manejo Transversal de Excepciones:** `BaseRouteBuilder` compartido con jerarquía de errores clara:
  * **Errores Funcionales / 4xx:** `BusinessValidationException`, `JsonParseException` -> Sin reintento, log a nivel WARN/INFO (sin volcado de StackTrace gigante), respuesta inmediata 400 Bad Request o 422 Unprocessable.
  * **Errores Transitorios / 5xx:** `HttpOperationFailedException`, `ConnectException`, `SocketTimeoutException` -> Política de Reintentos con Backoff Exponencial y Jitter.
* **Resiliencia Externa:** Patrón **Circuit Breaker** (Camel `circuitBreaker()` con Resilience4j) en llamadas a sistemas externos lentos o críticos.
* **Dead Letter Channel (DLC):** Para canales asíncronos (JMS / Kafka / Seda) con envío a cola/tópico `.DLQ` tras agotar reintentos.
* **Idempotencia:** EIP `idempotentConsumer` basado en clave de negocio (`orderId` o cabecera `X-Idempotency-Key`) sobre almacén distribuido (Redis o JDBC/BD).

### Configuración de Reintentos y StackTraces
```java
// Política estándar en BaseRouteBuilder
onException(TransientNetworkException.class, ConnectException.class)
    .maximumRedeliveries(3)
    .redeliveryDelay(1000)               // 1er reintento a 1s
    .backOffMultiplier(2.0)              // 2º a 2s, 3º a 4s
    .useExponentialBackOff()
    .retryAttemptedLogLevel(LoggingLevel.WARN)
    .logRetryStackTrace(false)           // LIMPIEZA DE LOGS
    .handled(true)
    .to("direct:handle-system-error");

onException(BusinessValidationException.class)
    .handled(true)
    .logHandled(true)
    .logStackTrace(false)                // NO ENSUCIAR CON TRACELOGS DE NEGOCIO
    .to("direct:handle-business-error");
```

### Formato de Salida al Cliente en Error (RFC 7807)
Si algo falla, el llamante jamás recibe un volcado Java (`NullPointerException at line 140`) ni un 500 genérico vacío:
```json
{
  "type": "https://api.desigual.com/errors/invalid-order",
  "title": "Business Validation Error",
  "status": 400,
  "detail": "El importe total de las líneas (150.00 EUR) no coincide con el total del pedido (180.00 EUR).",
  "instance": "/api/v1/orders/ORD-98214",
  "correlationId": "8f4b23a1-7c22-4d1e-9a03-bc89ef234a12",
  "timestamp": "2026-10-05T09:25:00Z"
}
```

---

### Opciones Descartadas y Por Qué

#### A. Descartado: Dejar que las excepciones floten al contenedor (StackTrace Dump)
* **Qué prometía:** No escribir código de gestión de excepciones.
* **Por qué se descartó:** Brecha de seguridad grave (expone versiones de librerías, nombres de servidores y rutas de ficheros internos al cliente) y pésima experiencia para el consumidor de la API.

#### B. Descartado: Reintentar indiscriminadamente cualquier error
* **Qué prometía:** "Por si acaso funciona a la segunda".
* **Por qué se descartó:** Si un mensaje tiene un error semántico o de validación (ej. falta el DNI o el total no cuadra), reintentar 5 veces es quemar CPU inútilmente, saturar los logs y retrasar la respuesta al cliente. Solo se reintentan fallos de transporte y de red transitorios.

#### C. Descartado: Reintentos síncronos en memoria sin límite ni backoff
* **Qué prometía:** `maximumRedeliveries(10)` seguidos sin delay.
* **Por qué se descartó:** El efecto *Thundering Herd* o "martilleo". Si el ERP se cae por sobrecarga, miles de peticiones reintentando al instante sin espera destruyen cualquier posibilidad de que el ERP se recupere. El uso de exponential backoff con jitter es innegociable.

---

Aquí tienes la respuesta técnica profunda para los puntos del **6 al 10**, completando el decálogo de la arquitectura estándar. Al igual que antes, cada decisión incluye su justificación práctica y el análisis de **las alternativas descartadas con sus motivos reales de descarte**.

---

# 6. Configuración por Entorno y Gestión de Secretos

### Decisión Estándar
* **Configuración Externa:** Principio *Build Once, Deploy Anywhere* (12-Factor App). Una única imagen de contenedor inmutable viaja de `DEV` a `PRE` y `PROD`.
* **Mecanismo:** Spring Boot Externalized Configuration jerárquica:
  1. Valores por defecto sanos en `src/main/resources/application.yml`.
  2. Sobreescritura en Kubernetes vía **`ConfigMaps`** (variables de entorno o ficheros montados en `/config`).
  3. Mapeo a POJOs inmutables mediante clases `@ConfigurationProperties(prefix = "integration...")` validadas con `@Validated` / Jakarta Bean Validation (`@NotNull`, `@Min`).
* **Gestión de Secretos:** 
  * Los secretos (passwords, tokens, certificados) **nunca** residen en Git ni en `application.yml`.
  * Se inyectan en runtime mediante **Kubernetes Secrets** (sincronizados con Azure Key Vault, AWS Secrets Manager o HashiCorp Vault vía External Secrets Operator).
  * En local, se utiliza un fichero no versionado (`application-local.yml` en `.gitignore`) o variables de entorno del sistema.

### ¿Por qué esta solución?
* **Seguridad y Auditoría:** Separación estricta entre configuración operativa y secretos confidenciales. Si un desarrollador clona el repo, no puede comprometer credenciales de producción.
* **Tipado Fuerte:** Con `@ConfigurationProperties`, si una URL o un timeout no están definidos o tienen un formato incorrecto, la aplicación **falla en el arranque (fail-fast)** con un mensaje explícito, en lugar de fallar en runtime durante la primera transacción en mitad de la noche.

---

### Opciones Descartadas y Por Qué

#### A. Descartado: Perfiles de Spring empaquetados en el JAR (`application-dev.yml`, `application-prod.yml`) con credenciales dentro
* **Qué prometía:** Comodidad para desplegar con `--spring.profiles.active=prod`.
* **Por qué se descartó:** Gravísimo fallo de seguridad (las credenciales de producción acaban en el historial de Git) y antipatrón de despliegue. No se debe recompilar ni tocar el paquete para cambiar de entorno.

#### B. Descartado: Apache ZooKeeper / Spring Cloud Config Server dedicado
* **Qué prometía:** Servidor centralizado de configuración dinámica en caliente.
* **Por qué se descartó:** Introduce un punto único de fallo (SPOF) y añade una pieza de infraestructura pesada innecesaria. En arquitecturas modernas de contenedores, Kubernetes ya resuelve esto de forma nativa y robusta con ConfigMaps y Secrets.

---

# 7. Observabilidad: Logs Estructurados, Trazabilidad, Health y Métricas

### Decisión Estándar
* **Logs Estructurados:** Formato **JSON en `stdout`** (usando Logstash Logback Encoder o Log4j2 JsonTemplateLayout). Cada log es un objeto JSON indexable directamente por Elasticsearch, Splunk o Datadog.
* **Correlación y Trazabilidad (Distributed Tracing):**
  * **W3C Trace Context (`traceparent`)** + **`X-Correlation-ID`**.
  * Al entrar una petición (HTTP, Kafka, JMS), un `CorrelationIdProcessor` inicializa el **MDC (Mapped Diagnostic Context)** con: `correlationId`, `traceId`, `spanId`, `routeId`.
  * Toda traza de log generada en ese hilo incluirá automáticamente esos campos sin que el desarrollador tenga que concatenarlos a mano.
* **Health Checks:** Spring Boot Actuator:
  * `/actuator/health/liveness`: Indica si el proceso Java está vivo (para que Kubernetes reinicie el pod si hay un deadlock).
  * `/actuator/health/readiness`: Camel evalúa el estado de las rutas y conexiones (broker, base de datos). Si una conexión se cae, devuelve `503` para que Kubernetes desvíe el tráfico sin reiniciar el pod.
* **Métricas:** `camel-micrometer-starter` + Actuator Prometheus (`/actuator/prometheus`). Expone métricas automáticas por ruta: `camel.route.exchanges.total`, `camel.route.exchanges.failed`, `camel.route.exchange.duration` (percentiles p50, p95, p99).

---

### Opciones Descartadas y Por Qué

#### A. Descartado: Logs en formato texto plano no estructurado (`System.out.println` o texto con formato `%d %p %m%n`)
* **Qué prometía:** Más fácil de leer en la consola local del desarrollador.
* **Por qué se descartó:** En entornos cloud con miles de pods concurrentes, los agregadores de logs (Datadog/Elastic) no pueden parsear eficientemente texto libre. Buscar errores en millones de líneas sin campos JSON estructurados es lento, costoso y requiere expresiones regulares frágiles.

#### B. Descartado: Instrumentación manual de métricas en cada ruta
* **Qué prometía:** Meter contadores a mano en cada `.process(...)`.
* **Por qué se descartó:** Duplica código, ensucia las rutas y cada desarrollador mide las cosas a su manera. Camel Micrometer instrumenta automáticamente todas las rutas con solo añadir la dependencia.

---

# 8. Estrategia de Pruebas: De la Pirámide a CI y Simulación

### Decisión Estándar: Pirámide de Pruebas en 4 Capas

```
        / \
       /   \      E2E Integration Tests (CamelTestSupport + WireMock) [Pocos, lentos]
      /-----\
     /       \    Contract Tests (OpenAPI / Spring Cloud Contract / Pact)
    /---------\
   /           \  Route & Flow Tests (Camel AdviceWith + MockEndpoints)
  /-------------\
 /               \ Unit Tests Puros (Mappers, Validadores, Entidades) [Muchos, ultra-rápidos]
-------------------
```

1. **Unit Tests Puros (JUnit 5 + AssertJ):**
   * Prueban lógica de dominio (`OrderBusinessValidatorTest`) y traductores (`OrderMapperTest`).
   * **Cero contexto de Spring y cero Camel:** Ejecutan en milisegundos sin levantar hilos ni frameworks.
2. **Route Testing (Pruebas de Mediación con Camel):**
   * Se utiliza `CamelTestSupport` o `@CamelSpringBootTest`.
   * Uso de **`AdviceWith`** para aislar la ruta: se reemplaza el endpoint de salida real (`to("http://erp...")`) por un `mock:erp-backend` para verificar cabeceras, bodies y asertos de número de intercambios.
3. **Simulación de Sistemas Externos (Integration Testing):**
   * **WireMock:** Se levanta un servidor HTTP mockeado en memoria dentro del test de integración. Se configuran *stubs* para simular escenarios de éxito (200 OK), errores funcionales (400 Bad Request) y caídas de red o timeouts (500 / 504 / drop connection) para verificar el circuito y los reintentos.
   * **Testcontainers:** Para dependencias como Kafka, ActiveMQ o Postgres en CI, se levantan contenedores efímeros Docker reales durante la fase de `mvn verify`.
4. **Ejecución en CI:**
   * `mvn test`: Corre tests unitarios y de ruta rápidos (falla en <1 minuto si algo está roto).
   * `mvn verify`: Corre tests de integración con WireMock/Testcontainers.

---

### Opciones Descartadas y Por Qué

#### A. Descartado: Conectarse a entornos de desarrollo reales externos (DEV/SANDBOX) en los tests
* **Qué prometía:** Probar contra el ERP de pruebas "de verdad".
* **Por qué se descartó:** Es la causa directa de builds inestables (*flaky tests*). Si el ERP de dev está caído, no tiene datos o alguien cambia una password, el pipeline de CI/CD del middleware se bloquea sin que el código tenga ningún error. Los tests de CI deben ser 100% deterministas y herméticos.

#### B. Descartado: Solo probar manualmente con Postman
* **Qué prometía:** Rapidez de entrega inicial sin escribir tests.
* **Por qué se descartó:** Destruye cualquier posibilidad de refactorización segura y provoca regresiones constantes en cada release.

---

# 9. Empaquetado, Despliegue y Versionado

### Decisión Estándar
* **Contenedorización:** **Multi-stage Dockerfile** con imagen base minimalista y segura:
  * Etapa 1 (Build): `maven:3.9-eclipse-temurin-21` (o compilación en runner CI).
  * Etapa 2 (Runtime): **`eclipse-temurin:21-jre-alpine`** o **Distroless** (`gcr.io/distroless/java21-debian12`).
  * Ejecución obligatoria con **usuario no-root** (`USER nonroot:nonroot`) por directrices CIS Benchmark.
* **Pipeline de CI/CD (GitHub Actions / GitLab CI / Azure DevOps):**
  1. **Lint & Security:** SonarQube + Trivy / Grype (escaneo de vulnerabilidades en dependencias y Dockerfile).
  2. **Build & Test:** `mvn clean verify` (unitarios + integración).
  3. **Container Build & Push:** Construcción de imagen y publicación en Container Registry (Harbor, ACR, ECR).
  4. **Deploy:** GitOps con **ArgoCD** actualizando manifiestos de Helm / Kustomize en el cluster.
* **Versionado:** **Semantic Versioning (SemVer 2.0.0)** (`MAJOR.MINOR.PATCH`):
  * `PATCH`: Bugfixes en rutas o mappers sin cambio de contrato.
  * `MINOR`: Nuevo canal de entrada o soporte de nuevo método/evento compatible hacia atrás.
  * `MAJOR`: Cambio incompatible en el modelo canónico o en los contratos expuestos al cliente.

---

### Opciones Descartadas y Por Qué

#### A. Descartado: Despliegue de artefactos JAR directos sobre VMs o Tomcat
* **Qué prometía:** Estilo clásico de administración de sistemas.
* **Por qué se descartó:** Incompatibilidad con la orquestación moderna, escalado automático horizontal elástico (HPA) y recuperación automática de caídas que proporciona Kubernetes.

#### B. Descartado: Construcción de imágenes Docker monolíticas con el JDK completo dentro
* **Qué prometía:** Simplicidad al usar una sola imagen `openjdk:21`.
* **Por qué se descartó:** Imágenes de más de 800 MB con herramientas de compilación innecesarias en runtime, lo que incrementa exponencialmente la superficie de ataque y los tiempos de despliegue en el cluster. Una imagen Distroless/JRE pesa menos de 200 MB y no tiene shells ni herramientas atacables.

---

# 10. Guía Práctica: "¿Cómo añado una integración nueva?"

Este es el manual del desarrollador del "primer día". El desarrollador no improvisa: sigue un flujo estricto paso a paso:

```
[Paso 1] Definir Contratos y Mappers
   │   • Crear DTO de entrada en inbound/{canal}/dto/
   │   • Si el modelo de dominio no tiene los campos, extender domain/model/ (Canónico)
   │   • Crear traductor en inbound/{canal}/mapper/
   ▼
[Paso 2] Implementar la Ruta Inbound
   │   • Crear clase en inbound/{canal}/{Canal}InboundRoute.java
   │   • Extender BaseRouteBuilder (hereda manejo de errores RFC 7807 y correlación)
   │   • Extraer headers, validar payload, transformar a Canónico y enviar a direct:orchestration.{caso}
   ▼
[Paso 3] Definir la Lógica de Negocio y Orquestación
   │   • Crear servicio en domain/service/{Caso}BusinessValidator.java (100% Java puro con test JUnit)
   │   • Crear ruta en orchestration/{caso}/{Caso}ProcessRoute.java
   │   • Enlazar validación y bifurcación (Choice/Multicast/Enricher)
   ▼
[Paso 4] Implementar el Adaptador de Salida (Outbound)
   │   • Crear DTO específico del backend en outbound/{sistema}/dto/
   │   • Crear mapper Canónico -> DTO Backend en outbound/{sistema}/mapper/
   │   • Crear ruta outbound/{sistema}/{Sistema}OutboundRoute.java con timeouts y circuit breaker
   ▼
[Paso 5] Configurar Propiedades
   │   • Registrar URLs, timeouts y reintentos en application.yml bajo su prefijo tipado
   ▼
[Paso 6] Crear Tests de Verificación
   │   • Test unitario del validador y mapper (sin Camel)
   │   • Test de integración end-to-end con WireMock simulando el backend
```
