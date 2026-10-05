package com.fluxaria.middleware.inbound.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fluxaria.middleware.domain.model.OrderStatus;

import java.math.BigDecimal;

/**
 * Contrato de salida devuelto al cliente tras procesar el pedido (HTTP 201 Created).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrderResponseDto(
        @JsonProperty("orderId")
        String orderId,

        @JsonProperty("customerId")
        String customerId,

        @JsonProperty("status")
        OrderStatus status,

        @JsonProperty("totalOriginal")
        BigDecimal totalOriginal,

        @JsonProperty("currencyOriginal")
        String currencyOriginal,

        @JsonProperty("totalEur")
        BigDecimal totalEur,

        @JsonProperty("exchangeRate")
        BigDecimal exchangeRate,

        @JsonProperty("countryName")
        String countryName,

        @JsonProperty("countryRegion")
        String countryRegion,

        @JsonProperty("phonePrefix")
        String phonePrefix,

        @JsonProperty("processedAt")
        String processedAt
) {
}