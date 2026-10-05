package com.fluxaria.middleware.outbound.kafka.dto;

import java.math.BigDecimal;

/**
 * Contrato de evento publicado en el topic `orders.batch.summary` de Apache Kafka.
 */
public record OrderBatchSummaryEventDto(
        String batchId,
        int totalOrders,
        int processedCount,
        int failedCount,
        BigDecimal totalEur,
        String processedAt,
        String correlationId
) {
}
