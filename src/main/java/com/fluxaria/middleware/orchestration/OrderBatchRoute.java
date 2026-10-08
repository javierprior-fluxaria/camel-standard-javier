package com.fluxaria.middleware.orchestration;

import com.fluxaria.middleware.domain.model.Order;
import com.fluxaria.middleware.orchestration.service.BatchAggregationService;
import com.fluxaria.middleware.shared.error.BaseRouteBuilder;
import com.fluxaria.middleware.shared.model.ErrorResponse;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.ProducerTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

/**
 * Orquestador de Proceso para Lotes de Pedidos (Capa de Orquestación / Aplicación).
 * Actúa como director de orquesta del lote:
 * 1. Inicializa metadatos del lote (Batch ID, total esperado).
 * 2. Divide la colección de pedidos mediante el patrón Splitter nativo de Camel.
 * 3. Reutiliza la orquestación canónica de pedido individual (OrderProcessRoute).
 * 4. Desvía pedidos fallidos a DLQ sin abortar el resto del lote.
 * 5. Agrega los resultados mediante BatchAggregationService.
 * 6. Publica el balance consolidado invocando a direct:outbound.kafka.batch-summary.
 */
@Component
public class OrderBatchRoute extends BaseRouteBuilder {

    public static final String ROUTE_ID = "orchestration.batch.process";
    public static final String DIRECT_PROCESS = "direct:" + ROUTE_ID;

    @Autowired
    private BatchAggregationService aggregationService;

    @Autowired
    private ProducerTemplate producerTemplate;

    @Override
    public void setupRoutes() {

        from(DIRECT_PROCESS)
                .routeId(ROUTE_ID)
                .log(LoggingLevel.INFO, "Iniciando orquestación de proceso de lote...")
                .process(exchange -> {
                    String batchId = exchange.getIn().getHeader("X-Batch-ID", String.class);
                    if (batchId == null || batchId.isBlank()) {
                        batchId = exchange.getProperty("batchId", String.class);
                    }
                    if (batchId == null || batchId.isBlank()) {
                        batchId = "BATCH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                    }
                    exchange.setProperty("batchId", batchId);
                    exchange.getIn().setHeader("X-Batch-ID", batchId);

                    Object body = exchange.getIn().getBody();
                    if (body instanceof Collection<?> col) {
                        exchange.setProperty("totalInBatch", col.size());
                    } else if (body instanceof Object[] arr) {
                        exchange.setProperty("totalInBatch", arr.length);
                    }
                })
                .log(LoggingLevel.INFO, "Procesando lote [${exchangeProperty.batchId}]...")

                // 1. Patrón Splitter con Agregación en capa de Orquestación
                .split(body(), aggregationService)
                    .stopOnException(false)
                    .to("direct:orchestration.batch.process-single-order")
                .end()

                // 2. Al completarse el lote, enviar el modelo de dominio BatchSummary a la salida
                .log(LoggingLevel.INFO, "Lote [${body.batchId}] orquestado con éxito: Total=${body.totalOrders}, OK=${body.processedCount}, Fallidos=${body.failedCount}, Total EUR=${body.totalEur}")
                .to("direct:outbound.kafka.batch-summary");

        // Subruta para procesar cada pedido individual con aislamiento de fallos
        from("direct:orchestration.batch.process-single-order")
                .routeId("orchestration.batch.process-single-order")
                .setProperty("originalPayload", body())
                .setHeader("X-Batch-ID", simple("${exchangeProperty.batchId}"))
                .doTry()
                    .process(exchange -> {
                        Order order = exchange.getIn().getBody(Order.class);
                        if (order != null && order.getOrderId() != null) {
                            exchange.setProperty("orderId", order.getOrderId());
                            exchange.getIn().setHeader("X-Order-ID", order.getOrderId());
                        }
                    })

                    // Invocar al Orquestador Canónico Unitario (REUTILIZACIÓN 100% FLUJO A)
                    .to(OrderProcessRoute.DIRECT_PROCESS)

                    // Evaluar el resultado de la orquestación
                    .process(exchange -> {
                        Object result = exchange.getIn().getBody();
                        Integer httpCode = exchange.getIn().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class);
                        if ((httpCode != null && httpCode >= 400) || (result instanceof ErrorResponse)) {
                            exchange.setProperty("orderFailed", true);
                            String reason = (result instanceof ErrorResponse err) ? err.detail() : ("HTTP " + httpCode + ": " + result);
                            exchange.getIn().setHeader("X-Failure-Reason", reason);
                            exchange.getIn().setHeader("X-Failed-At", Instant.now().toString());
                        } else if (result instanceof Order order) {
                            exchange.setProperty("orderFailed", false);
                            exchange.getIn().setBody(order);
                        } else {
                            exchange.setProperty("orderFailed", true);
                            exchange.getIn().setHeader("X-Failure-Reason", "Unknown orchestration result");
                            exchange.getIn().setHeader("X-Failed-At", Instant.now().toString());
                        }
                    })
                .doCatch(Exception.class)
                    .log(LoggingLevel.ERROR, "Fallo crítico al orquestar pedido en lote: ${exception.message}")
                    .setProperty("orderFailed", constant(true))
                    .setHeader("X-Failure-Reason", simple("${exception.message}"))
                    .setHeader("X-Failed-At", simple("${date:now:iso}"))
                .end()
                .choice()
                    .when(exchangeProperty("orderFailed").isEqualTo(true))
                        .to("direct:outbound.kafka.dlq")
                .end();
    }
}
