package com.fluxaria.middleware.outbound.country.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fluxaria.middleware.domain.model.CountryDetails;
import com.fluxaria.middleware.domain.model.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Map;

/**
 * Mapper Outbound: Traduce el contrato JSON de respuesta de RestCountries
 * al modelo de dominio CountryDetails / Order.
 */
@Component
public class CountryResponseMapper {

    private static final Logger log = LoggerFactory.getLogger(CountryResponseMapper.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CountryDetails toCountryDetails(String jsonResponse) {
        if (jsonResponse == null || jsonResponse.isBlank()) {
            return CountryDetails.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            JsonNode countryNode = root.isArray() && !root.isEmpty() ? root.get(0) : root;

            String officialName = countryNode.path("name").path("official").asText(null);
            String region = countryNode.path("region").asText(null);

            // Moneda local
            String localCurrency = null;
            JsonNode currenciesNode = countryNode.path("currencies");
            if (currenciesNode.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> fields = currenciesNode.fields();
                if (fields.hasNext()) {
                    localCurrency = fields.next().getKey();
                }
            }

            // Prefijo telefonico
            String phonePrefix = null;
            JsonNode iddNode = countryNode.path("idd");
            String rootPrefix = iddNode.path("root").asText("");
            JsonNode suffixesNode = iddNode.path("suffixes");
            String suffix = (suffixesNode.isArray() && !suffixesNode.isEmpty()) ? suffixesNode.get(0).asText("") : "";
            if (!rootPrefix.isBlank()) {
                phonePrefix = rootPrefix + suffix;
            }

            return new CountryDetails(officialName, region, localCurrency, phonePrefix);
        } catch (Exception e) {
            log.warn("Error en CountryResponseMapper al parsear respuesta: {}. Se devolveran datos vacios.", e.getMessage());
            return CountryDetails.empty();
        }
    }

    public Order enrichOrder(String jsonResponse, Order order) {
        CountryDetails details = toCountryDetails(jsonResponse);
        if (order != null) {
            order.enrichCountry(details);
        }
        return order;
    }
}