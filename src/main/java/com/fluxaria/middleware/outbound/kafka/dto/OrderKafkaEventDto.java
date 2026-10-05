package com.fluxaria.middleware.outbound.kafka.dto;

import java.math.BigDecimal;

/**
 * Contrato de evento publicado en el topic `orders.processed` de Apache Kafka.
 */
public record OrderKafkaEventDto(
        String eventType,
        String orderId,
        String customerId,
        String status,
        BigDecimal totalEur,
        BigDecimal exchangeRate,
        String correlationId,
        String timestamp
) {
}
