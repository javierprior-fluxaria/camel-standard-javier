package com.fluxaria.middleware.mapper;

import com.fluxaria.middleware.domain.model.CountryDetails;
import com.fluxaria.middleware.domain.model.Order;
import com.fluxaria.middleware.domain.model.OrderItem;
import com.fluxaria.middleware.outbound.country.mapper.CountryResponseMapper;
import com.fluxaria.middleware.outbound.currency.mapper.CurrencyConversionMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Tests Unitarios de Mappers Outbound: Country & Currency")
class ParsersTest {

    @Test
    @DisplayName("CountryResponseMapper debe mapear respuesta de RestCountries a CountryDetails")
    void testCountryMapper() {
        CountryResponseMapper mapper = new CountryResponseMapper();
        String json = """
                [
                  {
                    "name": {
                      "common": "Spain",
                      "official": "Kingdom of Spain"
                    },
                    "region": "Europe",
                    "currencies": {
                      "EUR": {
                        "name": "Euro",
                        "symbol": "€"
                      }
                    },
                    "idd": {
                      "root": "+3",
                      "suffixes": ["4"]
                    }
                  }
                ]
                """;

        CountryDetails details = mapper.toCountryDetails(json);

        assertNotNull(details);
        assertEquals("Kingdom of Spain", details.officialName());
        assertEquals("Europe", details.region());
        assertEquals("EUR", details.localCurrency());
        assertEquals("+34", details.phonePrefix());
    }

    @Test
    @DisplayName("CurrencyConversionMapper debe extraer tasa y Order debe calcular el total EUR")
    void testCurrencyMapper() {
        CurrencyConversionMapper mapper = new CurrencyConversionMapper();
        String json = """
                {
                  "result": "success",
                  "base_code": "USD",
                  "rates": {
                    "USD": 1,
                    "EUR": 0.925
                  }
                }
                """;

        BigDecimal rate = mapper.toExchangeRate(json);
        assertEquals(new BigDecimal("0.925000"), rate);

        Order order = Order.createNew(
                "ORD-1",
                "CUST-1",
                "US",
                "USD",
                List.of(OrderItem.of("P1", 2, new BigDecimal("50.00")))
        );

        mapper.applyRate(json, order);
        assertEquals(new BigDecimal("92.50"), order.getTotalEur());
        assertEquals(new BigDecimal("0.925000"), order.getExchangeRate());
    }
}