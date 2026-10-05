package com.fluxaria.middleware.outbound.erp.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

/**
 * Contrato propio del Legacy ERP (Dialecto tecnico del backend).
 */
public record LegacyErpOrderDto(
        @JsonProperty("legacyOrderId")
        String legacyOrderId,

        @JsonProperty("customerId")
        String customerId,

        @JsonProperty("destinationCountry")
        String destinationCountry,

        @JsonProperty("totalEurAmount")
        BigDecimal totalEurAmount,

        @JsonProperty("lines")
        List<LegacyErpLineDto> lines
) {
    public record LegacyErpLineDto(
            @JsonProperty("sku")
            String sku,
            @JsonProperty("qty")
            int qty,
            @JsonProperty("unitPriceEur")
            BigDecimal unitPriceEur
    ) {}
}