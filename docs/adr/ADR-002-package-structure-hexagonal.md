# ADR-002: Estructura de Paquetes Hexagonal y API-Led en Monomódulo

## Estado
**Aceptado**

## Contexto
El middleware debe ser comprensible y mantenible tanto para desarrolladores junior como senior, evitando la dispersión de ficheros relacionados y la complejidad innecesaria de múltiples módulos Maven para un único servicio.

## Decisión
Adoptar un proyecto mono-módulo Maven estructurado en capas concéntricas (Hexagonal / API-Led Connectivity):
- **`domain/`**: Núcleo canónico puro (`model/` con entidades y `service/` con validaciones).
- **`inbound/{canal}/`**: Adaptadores de entrada (Experience API) con subpaquetes `dto/`, `mapper/` y rutas.
- **`orchestration/`**: Capa de proceso agnóstica que une inbound y outbound sobre el modelo canónico.
- **`outbound/{sistema}/`**: Adaptadores de salida (System API) hacia ERPs, Base de Datos, Kafka o APIs externas con subpaquetes `dto/`, `mapper/` y rutas.
- **`shared/`**: Aspectos transversales (`error/`, `logging/`).

## Alternativas Descartadas
- **Multi-módulo Maven prematuro (`-core`, `-inbound`, `-outbound`):** Descartado por sobrecarga de mantenimiento de POMs y dependencias cíclicas en microservicios acotados.
- **Estructura por tipo de artefacto (`routes/`, `mappers/`, `models/`):** Descartado por violar la alta cohesión (modificar un sistema obligaba a saltar entre 5 carpetas distintas).

## Reglas para el Desarrollador
- Todo nuevo canal de entrada va en `inbound/{nombre_canal}/`.
- Todo nuevo sistema externo va en `outbound/{nombre_sistema}/`.
- Nunca cruzar llamadas directas entre adaptadores inbound y outbound sin pasar por la capa de orquestación.
