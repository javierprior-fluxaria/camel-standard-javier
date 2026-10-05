package com.fluxaria.middleware.outbound.kafka.mapper;

import com.fluxaria.middleware.domain.model.BatchSummary;
import com.fluxaria.middleware.domain.model.Order;
import com.fluxaria.middleware.outbound.kafka.dto.OrderBatchSummaryEventDto;
import com.fluxaria.middleware.outbound.kafka.dto.OrderKafkaEventDto;
import org.apache.camel.ExchangeProperty;
import org.springframework.stereotype.Component;

/**
 * Mapper Outbound: Transforma el modelo canonico Order y BatchSummary en eventos DTO para Kafka.
 */
@Component
public class OrderKafkaMapper {

    public OrderKafkaEventDto toEventDto(Order order, @ExchangeProperty("correlationId") String correlationId) {
        if (order == null) {
            return null;
        }
        return new OrderKafkaEventDto(
                "OrderProcessed",
                order.getOrderId(),
                order.getCustomerId(),
                order.getStatus() != null ? order.getStatus().name() : null,
                order.getTotalEur(),
                order.getExchangeRate(),
                correlationId != null ? correlationId : "N/A",
                order.getUpdatedAt() != null ? order.getUpdatedAt().toString() : null
        );
    }

    public OrderBatchSummaryEventDto toBatchSummaryEventDto(BatchSummary summary) {
        if (summary == null) {
            return null;
        }
        return new OrderBatchSummaryEventDto(
                summary.getBatchId(),
                summary.getTotalOrders(),
                summary.getProcessedCount(),
                summary.getFailedCount(),
                summary.getTotalEur(),
                summary.getProcessedAt() != null ? summary.getProcessedAt().toString() : null,
                summary.getCorrelationId()
        );
    }
}
