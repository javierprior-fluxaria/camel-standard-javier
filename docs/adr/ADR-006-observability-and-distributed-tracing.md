# ADR-006: Observabilidad Transversal y Correlación Distribuida

## Estado
**Aceptado**

## Contexto
En un entorno distribuido con múltiples componentes (REST, PostgreSQL, Kafka, ERP externo), es crítico rastrear el ciclo de vida completo de cada mensaje de extremo a extremo sin depender de búsquedas manuales inconexas.

## Decisión
1. **Identificador de Correlación (`X-Correlation-ID`):**
   - Si la petición entrante incluye la cabecera `X-Correlation-ID`, se preserva. Si no, `CorrelationIdProcessor` genera un UUID versión 4 automáticamente.
   - Se inyecta en el **MDC (Mapped Diagnostic Context)** de SLF4J bajo la clave `correlationId`.
   - Se propaga a todos los hilos y sistemas externos:
     - Cabecera HTTP en llamadas salientes.
     - Columna `correlation_id` en tablas de auditoría de PostgreSQL (`order_events`).
     - Cabecera del mensaje en Apache Kafka.
     - Campo `correlationId` en respuestas de error RFC 7807.
2. **Logs Estructurados:** Los logs de la aplicación deben incluir el `%X{correlationId}` para ser indexados por agregadores como Elasticsearch o Datadog.
3. **Métricas y Probes:**
   - Spring Boot Actuator expone `/actuator/health/liveness` y `/actuator/health/readiness`.
   - Micrometer Prometheus (`/actuator/prometheus`) expone métricas automáticas por ruta de Camel (`camel.route.exchanges.total`, percentiles de duración p95/p99).

## Reglas para el Desarrollador
- Toda ruta de entrada (`inbound`) debe invocar `.process(correlationIdProcessor)` como primer paso.
- Nunca imprimir logs usando `System.out.println`. Utilizar `log.info(...)` o `.log(...)` de Camel.
