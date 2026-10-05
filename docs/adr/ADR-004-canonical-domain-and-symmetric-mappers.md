# ADR-004: Modelo Canónico de Dominio y Simetría Inbound/Outbound de Mappers

## Estado
**Aceptado**

## Contexto
Diferentes sistemas externos manejan formatos dispares (JSON, XML, mapas JDBC, eventos Kafka). Acoplar las rutas a esquemas de transporte externos genera fragilidad ante cambios y duplica lógica de transformación.

## Decisión
1. **Modelo Canónico Puro (`domain/model/`):** Clases y Records Java 21 libres de dependencias de frameworks (sin anotaciones de Jackson, Camel ni JPA). Representa el concepto de negocio universal de la organización.
2. **DTOs de Frontera (`dto/`):** Clases dedicadas por canal/sistema con las anotaciones técnicas de transporte necesarias.
3. **Simetría Estricta de Mappers:**
   - Todo subsistema de entrada (`inbound/{canal}/mapper/`) traduce DTO externo -> Modelo Canónico.
   - Todo subsistema de salida (`outbound/{sistema}/mapper/`) traduce Modelo Canónico -> DTO externo o Map de parámetros SQL/JDBC.
   - Cada mapper es un `@Component` de Spring puro, probado con JUnit 5 sin necesidad de levantar Camel ni Spring Context.
4. **Restauración de Entidades en Rutas:** Al enviar datos a un sistema secundario o base de datos, el cuerpo del mensaje se restaura utilizando el DSL nativo `.setBody(exchangeProperty("miEntidad"))`, sin lambdas manuales.

## Alternativas Descartadas
- **Mapas genéricos `Map<String, Object>` sin tipar:** Descartado por provocar `ClassCastException` y pérdida total de contratos en compilación.
- **Herramientas de mapeo dinámico (AtlasMap / Dozer):** Descartadas por estar obsoletas o depender de reflexión lenta.

## Reglas para el Desarrollador
- Ninguna ruta de orquestación debe conocer DTOs de entrada o de salida.
- En salidas a Base de Datos (JDBC) o Kafka, implementa siempre un `*Mapper.java` en `outbound/{sistema}/mapper/` que prepare los mapas o el evento DTO.
