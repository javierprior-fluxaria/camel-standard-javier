package com.fluxaria.middleware.outbound.database.mapper;

import com.fluxaria.middleware.domain.model.Order;
import org.apache.camel.ExchangeProperty;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Mapper Outbound: Transforma el modelo canonico Order en mapas de parametros para camel-sql.
 */
@Component
public class OrderDatabaseMapper {

    public Map<String, Object> toInsertOrderParams(Order order) {
        if (order == null) {
            return Map.of();
        }
        Map<String, Object> params = new HashMap<>();
        params.put("orderId", order.getOrderId());
        params.put("customerId", order.getCustomerId());
        params.put("countryIso2", order.getCountryIso2());
        params.put("currency", order.getCurrency());
        params.put("totalOriginal", order.getTotalOriginal());
        params.put("totalEur", order.getTotalEur());
        params.put("exchangeRate", order.getExchangeRate());
        params.put("countryName", order.getCountryDetails() != null ? order.getCountryDetails().officialName() : null);
        params.put("countryRegion", order.getCountryDetails() != null ? order.getCountryDetails().region() : null);
        params.put("phonePrefix", order.getCountryDetails() != null ? order.getCountryDetails().phonePrefix() : null);
        params.put("status", order.getStatus() != null ? order.getStatus().name() : null);
        return params;
    }

    public Map<String, Object> toInitialAuditParams(@ExchangeProperty("currentOrder") Order order,
                                                    @ExchangeProperty("correlationId") String correlationId) {
        Map<String, Object> params = new HashMap<>();
        params.put("orderId", order != null ? order.getOrderId() : "N/A");
        params.put("eventType", "ORDER_ENRICHED");
        params.put("payload", "{\"status\":\"" + (order != null ? order.getStatus() : "") + "\",\"totalEur\":" + (order != null ? order.getTotalEur() : "0") + "}");
        params.put("correlationId", correlationId != null ? correlationId : "N/A");
        return params;
    }

    public Map<String, Object> toUpdateStatusParams(Order order) {
        if (order == null) {
            return Map.of();
        }
        Map<String, Object> params = new HashMap<>();
        params.put("orderId", order.getOrderId());
        params.put("status", order.getStatus() != null ? order.getStatus().name() : null);
        return params;
    }

    public Map<String, Object> toConfirmedAuditParams(@ExchangeProperty("currentOrder") Order order,
                                                      @ExchangeProperty("correlationId") String correlationId) {
        Map<String, Object> params = new HashMap<>();
        params.put("orderId", order != null ? order.getOrderId() : "N/A");
        params.put("eventType", "ORDER_CONFIRMED");
        params.put("payload", "{\"status\":\"CONFIRMED\"}");
        params.put("correlationId", correlationId != null ? correlationId : "N/A");
        return params;
    }
}
