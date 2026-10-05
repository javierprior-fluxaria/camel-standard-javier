package com.fluxaria.middleware.outbound.kafka.mapper;

import com.fluxaria.middleware.domain.model.Order;
import com.fluxaria.middleware.outbound.kafka.dto.OrderKafkaEventDto;
import org.apache.camel.ExchangeProperty;
import org.springframework.stereotype.Component;

/**
 * Mapper Outbound: Transforma el modelo canonico Order en el evento DTO para Kafka.
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
}
