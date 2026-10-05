package com.fluxaria.middleware.inbound.kafka.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * Contrato DTO para una línea de producto dentro de un pedido en lote (orders.batch.in).
 */
public record OrderBatchItemProductDto(
        @JsonProperty("productId")
        String productId,

        @JsonProperty("quantity")
        Integer quantity,

        @JsonProperty("unitPrice")
        BigDecimal unitPrice
) {
}
