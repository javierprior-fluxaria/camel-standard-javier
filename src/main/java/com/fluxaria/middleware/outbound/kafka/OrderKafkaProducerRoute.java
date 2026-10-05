package com.fluxaria.middleware.outbound.kafka;

import com.fluxaria.middleware.shared.error.BaseRouteBuilder;
import org.apache.camel.LoggingLevel;
import org.springframework.stereotype.Component;

/**
 * Adaptador Outbound para publicacion de eventos en Kafka (orders.processed / orders.failed).
 */
@Component
public class OrderKafkaProducerRoute extends BaseRouteBuilder {

    public static final String DIRECT_PUBLISH = "direct:outbound.kafka.publish";

    @Override
    public void setupRoutes() {
        from(DIRECT_PUBLISH)
                .routeId("outbound.kafka.publish")
                .log(LoggingLevel.INFO, "Publicando evento OrderProcessed en Kafka para pedido ${body.orderId} con Correlation-ID: ${exchangeProperty.correlationId}")
                // Se completara el envio al topic de Kafka en la fase de integracion externa
                ;
    }
}