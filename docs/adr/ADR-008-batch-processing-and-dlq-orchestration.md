# ADR-008: Orquestación Asíncrona de Lotes (Splitter + Aggregator) y Política Dead Letter Queue

## Estado
**Aceptado**

## Contexto
El middleware debe procesar cargas asíncronas masivas (lotes de pedidos en Kafka `orders.batch.in`) con escenarios mixtos (pedidos válidos, inválidos y errores de integración). Se debe maximizar la reutilización del flujo unitario síncrono existente (Flujo A) sin duplicar lógica de negocio, reportar un resumen consolidado (`orders.batch.summary`) y garantizar que ningún pedido fallido se pierda silenciosamente (`orders.dlq`).

## Decisión
1. **Contratos Tipados de Entrada en Inbound (`inbound/kafka/`):**
   - El consumidor deserializa el mensaje JSON en DTOs explícitos (`OrderBatchItemDto[]`).
   - El traductor `OrderBatchInboundMapper` transforma el array de DTOs en una lista canónica de dominio (`List<Order>`).
   - El adaptador Inbound solo transporta y delega; no orquesta ni publica a colas secundarias salvo fallo de sintaxis fatal que va a DLQ.

2. **Reutilización Canónica mediante Splitter EIP (`orchestration/OrderBatchRoute.java`):**
   - La orquestación del lote recibe la colección de dominio y la divide usando el patrón Splitter nativo de Camel.
   - Cada pedido individual es procesado invocando exactamente la misma ruta canónica que el flujo REST: `to(OrderProcessRoute.DIRECT_PROCESS)`.
   - Se aísla cada ejecución con `stopOnException(false)` y bloques `doTry/doCatch` para que un pedido defectuoso no aborte el procesamiento de los demás pedidos del lote.

3. **Ubicación de la Lógica de Proceso (`orchestration/service/BatchAggregationService.java`):**
   - La estrategia de agregación implementa `AggregationStrategy` de Apache Camel y vive como servicio de aplicación en `orchestration/service/`.
   - Acumula métricas operativas (procesados, fallidos, total EUR) sobre la entidad pura de dominio `BatchSummary`.
   - Se mantiene el paquete `domain/` 100% puro y libre de dependencias de Camel.

4. **Política Dead Letter Queue ("Nada se pierde en silencio"):**
   - Los pedidos que fallen por validación de negocio, rechazo de backend o errores de parsing se desvían inmediatamente a `direct:outbound.kafka.dlq` hacia el topic `orders.dlq`.
   - Se conservan el payload original del pedido, el identificador (`X-Order-ID` / `X-Batch-ID`) y el motivo exacto del fallo en la cabecera `X-Failure-Reason`.

5. **Publicación Simétrica de Resumen (`outbound/kafka/`):**
   - Al finalizar el Splitter, el `BatchSummary` resultante es traducido por `OrderKafkaMapper` a `OrderBatchSummaryEventDto` y publicado en `orders.batch.summary`.

## Alternativas Descartadas
- **Llamadas directas de Inbound Kafka a Outbound Kafka:** Descartado por violar la Arquitectura Hexagonal y acoplar el adaptador de entrada con el broker de salida sin pasar por la capa de proceso.
- **Estrategia de Agregación dentro del paquete `domain/`:** Descartado porque `AggregationStrategy` pertenece a la API de Apache Camel y contaminaría el dominio puro.
- **Creación de subdirectorios anidados `orchestration/batch/`:** Descartado para mantener una arquitectura plana, simple y simétrica con el orquestador unitario.

## Reglas para el Desarrollador
- Toda nueva integración por lotes debe definir sus DTOs de entrada y mapper en `inbound/{canal}/`.
- La orquestación debe operar sobre colecciones del modelo canónico (`List<T>`) y reutilizar rutas unitarias existentes.
- Los fallos en lotes deben reportarse siempre a la DLQ correspondiente documentando la causa en la cabecera `X-Failure-Reason`.
