package com.fluxaria.middleware.domain.model;

import java.math.BigDecimal;

/**
 * Conversión de divisa a EUR con la tasa de cambio aplicada.
 * Objeto de valor puro e inmutable.
 */
public record CurrencyConversion(
        String fromCurrency,
        String toCurrency,
        BigDecimal exchangeRate,
        BigDecimal convertedTotal
) {
}