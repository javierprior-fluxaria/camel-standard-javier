package com.fluxaria.middleware.inbound.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * DTO para las líneas de pedido recibidas en el POST HTTP.
 */
public record OrderItemDto(
        @NotBlank(message = "productId is required")
        @JsonProperty("productId")
        String productId,

        @Min(value = 1, message = "quantity must be greater than 0")
        @JsonProperty("quantity")
        int quantity,

        @NotNull(message = "unitPrice is required")
        @DecimalMin(value = "0.01", message = "unitPrice must be greater than 0")
        @JsonProperty("unitPrice")
        BigDecimal unitPrice
) {
}