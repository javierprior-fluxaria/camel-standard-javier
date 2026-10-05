package com.fluxaria.middleware.outbound.currency.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fluxaria.middleware.domain.model.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Mapper Outbound: Extrae y traduce la tasa de cambio desde el contrato JSON
 * de la API externa (open.er-api / Frankfurter) y la aplica al objeto de Dominio.
 */
@Component
public class CurrencyConversionMapper {

    private static final Logger log = LoggerFactory.getLogger(CurrencyConversionMapper.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    public BigDecimal toExchangeRate(String jsonResponse) {
        if (jsonResponse == null || jsonResponse.isBlank()) {
            return BigDecimal.ONE;
        }
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            JsonNode ratesNode = root.path("rates");
            double eurRate = ratesNode.path("EUR").asDouble(1.0);
            return BigDecimal.valueOf(eurRate).setScale(6, RoundingMode.HALF_UP);
        } catch (Exception e) {
            log.warn("Error en CurrencyConversionMapper al parsear tasa EUR: {}. Se aplicara paridad 1.0.", e.getMessage());
            return BigDecimal.ONE;
        }
    }

    public Order applyRate(String jsonResponse, Order order) {
        BigDecimal rate = toExchangeRate(jsonResponse);
        if (order != null) {
            order.applyExchangeRate(rate);
        }
        return order;
    }
}