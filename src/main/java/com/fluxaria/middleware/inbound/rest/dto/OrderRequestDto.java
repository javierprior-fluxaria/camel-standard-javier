package com.fluxaria.middleware.inbound.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Contrato de entrada para crear un pedido: POST /api/v1/orders
 */
public record OrderRequestDto(
        @JsonProperty("orderId")
        String orderId,

        @NotBlank(message = "customerId is required")
        @JsonProperty("customerId")
        String customerId,

        @NotBlank(message = "country is required")
        @Size(min = 2, max = 2, message = "country must be an ISO-2 alpha code")
        @JsonProperty("country")
        String country,

        @NotBlank(message = "currency is required")
        @Size(min = 3, max = 3, message = "currency must be an ISO-4217 code")
        @JsonProperty("currency")
        String currency,

        @NotEmpty(message = "items cannot be empty")
        @Valid
        @JsonProperty("items")
        List<OrderItemDto> items
) {
}