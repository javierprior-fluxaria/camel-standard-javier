package com.fluxaria.middleware.inbound.kafka.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Contrato DTO para un pedido individual dentro de un lote consumido desde Apache Kafka (topic orders.batch.in).
 */
public record OrderBatchItemDto(
        @JsonProperty("orderId")
        String orderId,

        @JsonProperty("customerId")
        String customerId,

        @JsonProperty("country")
        String country,

        @JsonProperty("currency")
        String currency,

        @JsonProperty("items")
        List<OrderBatchItemProductDto> items
) {
}
