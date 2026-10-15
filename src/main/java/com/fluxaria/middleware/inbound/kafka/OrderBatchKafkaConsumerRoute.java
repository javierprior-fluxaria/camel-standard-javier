package com.fluxaria.middleware.inbound.kafka;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fluxaria.middleware.inbound.kafka.dto.OrderBatchItemDto;
import com.fluxaria.middleware.inbound.kafka.mapper.OrderBatchInboundMapper;
import com.fluxaria.middleware.orchestration.OrderBatchRoute;
import com.fluxaria.middleware.shared.error.BaseRouteBuilder;
import com.fluxaria.middleware.shared.logging.CorrelationIdProcessor;
import org.apache.camel.LoggingLevel;
import org.apache.camel.component.jackson.JacksonDataFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Adaptador Inbound para consumo asíncrono de lotes de pedidos desde Apache Kafka (orders.batch.in).
 * Responsabilidades:
 * 1. Consumir el topic de Kafka con grupo de consumidores configurado.
 * 2. Propagar identificadores de trazabilidad (X-Correlation-ID y X-Batch-ID).
 * 3. Deserializar el payload JSON entrante en DTOs tipados (OrderBatchItemDto[]).
 * 4. Mapear DTOs al modelo canónico de dominio (List<Order>).
 * 5. Delegar en la capa de Orquestación (OrderBatchRoute.DIRECT_PROCESS).
 * 6. Si el payload JSON está totalmente corrupto y no puede parsearse, desviarlo a orders.dlq ("nada se pierde en silencio").
 */
@Component
public class OrderBatchKafkaConsumerRoute extends BaseRouteBuilder {

    public static final String ROUTE_ID = "inbound.kafka.batch-consumer";

    @Value("${integration.kafka.brokers:localhost:9092}")
    private String kafkaBrokers;

    @Value("${integration.kafka.topic-orders-batch-in:orders.batch.in}")
    private String topicBatchIn;

    @Value("${integration.kafka.batch-consumer-group:fluxaria-orders-batch-group}")
    private String consumerGroup;

    @Autowired
    private CorrelationIdProcessor correlationIdProcessor;

    @Autowired
    private OrderBatchInboundMapper batchInboundMapper;

    @Override
    public void setupRoutes() {
        JacksonDataFormat jacksonDataFormat = new JacksonDataFormat(OrderBatchItemDto[].class);
        jacksonDataFormat.disableFeature(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        jacksonDataFormat.enableFeature(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);

        from("kafka:" + topicBatchIn + "?brokers=" + kafkaBrokers + "&groupId=" + consumerGroup + "&autoOffsetReset=earliest")
                .routeId(ROUTE_ID)
                .log(LoggingLevel.INFO, "Recibido nuevo mensaje en topic '" + topicBatchIn + "'")
                .process(correlationIdProcessor)
                .process(exchange -> {
                    String batchId = exchange.getIn().getHeader("X-Batch-ID", String.class);
                    if (batchId == null || batchId.isBlank()) {
                        batchId = "BATCH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                        exchange.getIn().setHeader("X-Batch-ID", batchId);
                    }
                    exchange.setProperty("batchId", batchId);
                })
                .setProperty("originalPayload", body())
                .doTry()
                    // 1. Mapear a entidades de Dominio (maneja array directo o envuelto en objeto)
                    .bean(batchInboundMapper, "toDomainList")
                    // 2. Invocar al orquestador de lotes
                    .to(OrderBatchRoute.DIRECT_PROCESS)
                .doCatch(Exception.class)
                    .log(LoggingLevel.ERROR, "Fallo al deserializar lote de Kafka en topic '" + topicBatchIn + "': ${exception.message}")
                    .setHeader("X-Failure-Reason", simple("Malformed batch payload: ${exception.message}"))
                    .setHeader("X-Failed-At", simple("${date:now:iso}"))
                    .to("direct:outbound.kafka.dlq")
                .end();
    }
}
