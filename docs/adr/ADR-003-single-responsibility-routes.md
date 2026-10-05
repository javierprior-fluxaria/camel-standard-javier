# ADR-003: Responsabilidad Única de Rutas y Prohibición de Lógica en DSL

## Estado
**Aceptado**

## Contexto
En integraciones tradicionales con Apache Camel es habitual encontrar "rutas monstruo" de cientos de líneas donde se mezclan llamadas HTTP, bucles for, validaciones y transformaciones de datos dentro de lambdas imperativas `.process(...)`, resultando en código ininteligible e imposible de probar unitariamente.

## Decisión
1. **Límite de tamaño:** Ninguna clase `RouteBuilder` debe superar las 50-80 líneas de código Java.
2. **DSL declarativo:** El DSL de Camel se reserva exclusivamente para orquestación, enrutamiento y control de transporte (`.bean()`, `.to()`, `.marshal()`, `.circuitBreaker()`).
3. **Prohibición expresa de lambdas computacionales:** Se prohíbe el uso de `.process(exchange -> { ... })` para lógica de negocio, serialización manual con `ObjectMapper` o manipulación de colecciones. Estas tareas deben delegarse a Beans Spring (`.bean(miBean, "metodo")`).
4. **Convención de Nombres de Ruta:** Toda ruta debe declarar un `routeId` explícito con el formato `{capa}.{canal/sistema}.{accion}` (ej. `inbound.rest.receive-order`, `outbound.erp.submit`). Se prohíben IDs autogenerados (`route1`, `route2`).

## Reglas para el Desarrollador
- Si necesitas transformar datos, crea un método en el `@Component` de `mapper/`.
- Si necesitas validar reglas de negocio, crea un método en el `@Component` de `domain/service/`.
- Usa `direct:{routeId}` para llamadas internas síncronas entre capas.
