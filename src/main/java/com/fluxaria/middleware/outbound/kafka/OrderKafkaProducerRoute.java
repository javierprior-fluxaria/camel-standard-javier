package com.fluxaria.middleware.outbound.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fluxaria.middleware.domain.model.Order;
import com.fluxaria.middleware.outbound.kafka.mapper.OrderKafkaMapper;
import com.fluxaria.middleware.shared.error.BaseRouteBuilder;
import com.fluxaria.middleware.shared.logging.CorrelationIdProcessor;
import org.apache.camel.LoggingLevel;
import org.apache.camel.component.kafka.KafkaConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Adaptador Outbound para publicacion de eventos en Apache Kafka.
 * Publica en el topic `orders.processed` con:
 * - Clave = orderId (particionamiento por pedido)
 * - Header = X-Correlation-ID
 * - Payload = JSON con datos del pedido procesado
 */
@Component
public class OrderKafkaProducerRoute extends BaseRouteBuilder {

    public static final String ROUTE_ID = "outbound.kafka.publish";
    public static final String DIRECT_PUBLISH = "direct:" + ROUTE_ID;

    @Value("${integration.kafka.brokers:localhost:9092}")
    private String kafkaBrokers;

    @Value("${integration.kafka.topic-orders-processed:orders.processed}")
    private String topicOrdersProcessed;

    @Value("${integration.kafka.topic-orders-batch-summary:orders.batch.summary}")
    private String topicBatchSummary;

    @Value("${integration.kafka.topic-orders-dlq:orders.dlq}")
    private String topicDlq;

    @Autowired
    private OrderKafkaMapper orderKafkaMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public void setupRoutes() {
        // 1. Publicar pedido procesado individual
        from(DIRECT_PUBLISH)
                .routeId(ROUTE_ID)
                .log(LoggingLevel.INFO, "Publicando evento OrderProcessed en Kafka para pedido: ${body.orderId}")
                .setProperty("completedOrder", body())
                .setHeader(KafkaConstants.KEY, simple("${body.orderId}"))
                .setHeader(CorrelationIdProcessor.CORRELATION_HEADER, simple("${exchangeProperty.correlationId}"))
                .bean(orderKafkaMapper, "toEventDto")
                .marshal().json()
                .toD("kafka:" + topicOrdersProcessed + "?brokers=" + kafkaBrokers)
                .log(LoggingLevel.INFO, "Evento publicado satisfactoriamente en Kafka topic '" + topicOrdersProcessed + "'")
                .setBody(exchangeProperty("completedOrder"));

        // 2. Publicar resumen de lote
        from("direct:outbound.kafka.batch-summary")
                .routeId("outbound.kafka.batch-summary")
                .log(LoggingLevel.INFO, "Publicando resumen de lote en Kafka topic '" + topicBatchSummary + "' para batchId: ${body.batchId}")
                .setHeader(KafkaConstants.KEY, simple("${body.batchId}"))
                .setHeader(CorrelationIdProcessor.CORRELATION_HEADER, simple("${body.correlationId}"))
                .bean(orderKafkaMapper, "toBatchSummaryEventDto")
                .marshal().json()
                .toD("kafka:" + topicBatchSummary + "?brokers=" + kafkaBrokers)
                .log(LoggingLevel.INFO, "Resumen de lote publicado exitosamente en Kafka");

        // 3. Publicar pedidos fallidos a Dead Letter Queue (DLQ)
        from("direct:outbound.kafka.dlq")
                .routeId("outbound.kafka.dlq")
                .log(LoggingLevel.WARN, "Desviando pedido fallido a Dead Letter Queue '" + topicDlq + "'. Motivo: ${header.X-Failure-Reason}")
                .process(exchange -> {
                    String key = exchange.getIn().getHeader("X-Order-ID", String.class);
                    if (key == null || key.isBlank()) {
                        key = exchange.getProperty("orderId", String.class);
                    }
                    if (key == null || key.isBlank()) {
                        key = exchange.getProperty("correlationId", String.class);
                    }
                    if (key == null || key.isBlank()) {
                        key = exchange.getIn().getHeader(CorrelationIdProcessor.CORRELATION_HEADER, String.class);
                    }

                    Object payload = exchange.getProperty("originalPayload");
                    if (payload == null) {
                        payload = exchange.getIn().getBody();
                    }
                    if (payload instanceof String s) {
                        exchange.getIn().setBody(s);
                    } else if (payload != null) {
                        exchange.getIn().setBody(objectMapper.writeValueAsString(payload));
                    }

                    // Limpiar cabeceras Kafka entrantes del consumidor para evitar colisiones de topic/particion
                    exchange.getIn().removeHeaders("kafka.*");
                    exchange.getIn().setHeader(KafkaConstants.KEY, key);
                    exchange.getIn().setHeader(KafkaConstants.OVERRIDE_TOPIC, topicDlq);
                })
                .toD("kafka:" + topicDlq + "?brokers=" + kafkaBrokers)
                .log(LoggingLevel.INFO, "Pedido fallido depositado en DLQ '" + topicDlq + "' exitosamente");
    }
}