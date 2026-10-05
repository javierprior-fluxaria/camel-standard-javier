# ADR-005: Manejo de Errores RFC 7807 y Patrones de Resiliencia

## Estado
**Aceptado**

## Contexto
Los fallos en integraciones middleware deben comunicarse de forma estandarizada a los clientes HTTP sin exponer stacktraces internos que comprometan la seguridad, al tiempo que se deben tolerar caídas transitorias de backends sin degradar el servicio completo.

## Decisión
1. **Contrato de Error RFC 7807:** Toda respuesta de error en endpoints HTTP sigue el estándar internacional *Problem Details for HTTP APIs* (`application/problem+json`), con campos obligatorios: `type`, `title`, `status`, `detail`, `instance`, `correlationId`, `timestamp` y `invalidParams` (para 400).
2. **`BaseRouteBuilder` Transversal:** Todas las clases de rutas heredan de `BaseRouteBuilder`, que centraliza el manejo de excepciones mediante helpers reutilizables (`buildProblemDetails` y `buildValidationProblemDetails`):
   - **400 Bad Request:** Excepciones funcionales (`BusinessValidationException`). Log a nivel WARN sin volcado de StackTrace (`logStackTrace(false)`).
   - **409 Conflict:** Idempotencia en Base de Datos (`DuplicateKeyException`, `SQLException`) o conflicto en backends (`HttpOperationFailedException` con código 409).
   - **500 Internal Error:** Excepciones no controladas mapeadas a Problem Details genérico sin exponer detalles del sistema.
3. **Resiliencia Externa:**
   - **Circuit Breaker (Resilience4j):** Aplicado en llamadas a sistemas externos críticos o lentos (ej. ERP) para cortar el tráfico ante caídas sostenidas y permitir recuperación.
   - **Reintentos Inteligentes:** Reintentos con Backoff Exponencial aplicados **únicamente** a errores transitorios de red (`SocketTimeoutException`, `ConnectException`) o códigos HTTP 500/502. **Prohibido** reintentar errores funcionales 4xx.

## Reglas para el Desarrollador
- Toda nueva clase de ruta debe hacer `extends BaseRouteBuilder` e implementar `setupRoutes()`.
- Lanza siempre `BusinessValidationException` desde los validadores de negocio cuando el payload viole reglas funcionales.
