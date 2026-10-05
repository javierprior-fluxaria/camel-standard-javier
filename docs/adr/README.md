# Architectural Decision Records (ADRs) - Fluxaria Middleware

Este directorio contiene las decisiones arquitecturales clave (ADRs) que rigen el estándar de desarrollo de integraciones con Apache Camel 4 y Spring Boot 3. 

Cada desarrollador debe consultar estos registros para asegurar que cualquier nuevo desarrollo cumpla las reglas del estándar:

| ID | Título | Estado | Fichero |
|---|---|---|---|
| **ADR-001** | Runtime y Stack Tecnológico (Java 21, Camel 4.x, Spring Boot 3.3.x) | Aceptado | [ADR-001-runtime-stack.md](./ADR-001-runtime-stack.md) |
| **ADR-002** | Estructura de Paquetes Hexagonal y API-Led en Monomódulo | Aceptado | [ADR-002-package-structure-hexagonal.md](./ADR-002-package-structure-hexagonal.md) |
| **ADR-003** | Responsabilidad Única de Rutas y Prohibición de Lógica en DSL | Aceptado | [ADR-003-single-responsibility-routes.md](./ADR-003-single-responsibility-routes.md) |
| **ADR-004** | Modelo Canónico de Dominio y Simetría Inbound/Outbound de Mappers | Aceptado | [ADR-004-canonical-domain-and-symmetric-mappers.md](./ADR-004-canonical-domain-and-symmetric-mappers.md) |
| **ADR-005** | Manejo de Errores RFC 7807 y Patrones de Resiliencia | Aceptado | [ADR-005-rfc7807-error-handling-and-resilience.md](./ADR-005-rfc7807-error-handling-and-resilience.md) |
| **ADR-006** | Observabilidad Transversal y Correlación Distribuida (MDC + W3C) | Aceptado | [ADR-006-observability-and-distributed-tracing.md](./ADR-006-observability-and-distributed-tracing.md) |
| **ADR-007** | Estrategia de Pruebas Piramidal y Automatización E2E | Aceptado | [ADR-007-testing-strategy.md](./ADR-007-testing-strategy.md) |
| **ADR-008** | Orquestación Asíncrona de Lotes (Splitter + Aggregator) y Política DLQ | Aceptado | [ADR-008-batch-processing-and-dlq-orchestration.md](./ADR-008-batch-processing-and-dlq-orchestration.md) |

---
Para una justificación exhaustiva, análisis comparativo y alternativas descartadas en profundidad, consultar [`docs/architecture-decisions.md`](../architecture-decisions.md).
Para la guía paso a paso de implementación, consultar la skill del proyecto [`.skill/add-camel-integration/SKILL.md`](../../.skill/add-camel-integration/SKILL.md).
