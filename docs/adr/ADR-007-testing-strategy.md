# ADR-007: Estrategia de Pruebas Piramidal y Automatización E2E

## Estado
**Aceptado**

## Contexto
Los desarrollos middleware requieren pruebas rápidas y fiables que no dependan de entornos externos inestables (evitando *flaky tests* en CI/CD), garantizando al mismo tiempo que las integraciones completas funcionan de extremo a extremo.

## Decisión
Adoptar una pirámide de pruebas en tres niveles:
1. **Tests Unitarios Puros (JUnit 5 + AssertJ):**
   - Cobertura de validadores de negocio (`OrderBusinessValidatorTest`) y mappers (`OrderInboundMapperTest`, `OutboundMappersTest`, `ParsersTest`).
   - **Regla estricta:** Cero contexto de Spring y cero Camel. Ejecutan en milisegundos sin levantar puertos ni contenedores.
2. **Simulación de Sistemas Externos en Desarrollo/CI:**
   - La aplicación incluye un simulador desacoplado (`MockErpBackendRoute`) que reproduce escenarios deterministas (CUST-OK, CUST-SLOW, CUST-ERR500, CUST-ERR503, CUST-DUP).
   - En `docker-compose.yml`, los servicios de soporte (PostgreSQL y Kafka KRaft) se levantan localmente.
3. **Batería de Pruebas End-to-End:**
   - Script universal de PowerShell (`test-orders.ps1`) usando `Invoke-WebRequest -UseBasicParsing` compatible con Windows PowerShell 5.1 y PowerShell 7+.
   - Generación de identificadores de pedido dinámicos (`ORD-OK-$suffix`) para permitir ejecuciones repetibles sin conflictos de base de datos.
   - Colección de pruebas en Postman documentada en `docs/test.md`.

## Reglas para el Desarrollador
- Todo mapper o validador nuevo debe tener su test unitario en `src/test/java/`.
- Ejecutar `mvn test` antes de cualquier commit para validar la suite en local.
- Para pruebas de integración, levantar Docker Compose y verificar los escenarios con `.\test-orders.ps1`.
