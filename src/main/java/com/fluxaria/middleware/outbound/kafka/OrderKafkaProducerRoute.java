package com.fluxaria.middleware.outbound.kafka;

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

    @Autowired
    private OrderKafkaMapper orderKafkaMapper;

    @Override
    public void setupRoutes() {
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
    }
}