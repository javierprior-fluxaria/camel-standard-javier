# ADR-001: Runtime y Stack Tecnológico (Java 21 LTS + Apache Camel 4.x + Spring Boot 3.3.x)

## Estado
**Aceptado**

## Contexto
Se requiere estandarizar la plataforma técnica del middleware corporativo de Fluxaria para garantizar soporte a largo plazo, compatibilidad con ecosistemas cloud modernos, observabilidad nativa y rendimiento con Virtual Threads.

## Decisión
1. **Java:** OpenJDK 21 LTS (baseline mandatorio, uso de Virtual Threads, Java Records y Pattern Matching).
2. **Framework:** Spring Boot 3.3.x+ (soporte nativo de Kubernetes Actuator, Prometheus y perfiles tipados).
3. **Motor de Integración:** Apache Camel 4.x LTS (compatibilidad `jakarta.*`, optimización de memoria por Exchange y Virtual Threads en thread pools).

## Alternativas Descartadas
- **Camel Quarkus:** Descartado por fricción en compilación nativa con drivers y librerías legacy de clientes.
- **Camel Main Standalone:** Descartado por requerir reinventar la observabilidad y configuración que Spring Boot resuelve de serie.
- **Java 17 / 11:** Descartados por fin de vida y ausencia de Virtual Threads de serie.

## Reglas para el Desarrollador
- Todo nuevo microservicio debe utilizar este baseline de `pom.xml`.
- Utilizar Java Records para contratos inmutables siempre que sea posible.
