package com.fluxaria.middleware.outbound.country;

import com.fluxaria.middleware.outbound.country.mapper.CountryResponseMapper;
import com.fluxaria.middleware.shared.error.BaseRouteBuilder;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Adaptador Outbound para el enriquecimiento de datos de pais via RestCountries:
 * https://restcountries.com/v3.1/alpha/{iso2}
 */
@Component
public class CountryEnricherRoute extends BaseRouteBuilder {

    public static final String ROUTE_ID = "outbound.country.enrich";
    public static final String DIRECT_ENRICH = "direct:" + ROUTE_ID;

    @Value("${integration.outbound.country.base-url:https://restcountries.com/v3.1/alpha}")
    private String countryApiBaseUrl;

    @Autowired
    private CountryResponseMapper countryResponseMapper;

    @Override
    public void setupRoutes() {
        from(DIRECT_ENRICH)
                .routeId(ROUTE_ID)
                .log(LoggingLevel.INFO, "Iniciando enriquecimiento de pais para pedido ${body.orderId} con ISO-2: ${body.countryIso2}")
                .setProperty("originalOrder", body())
                .setHeader(Exchange.HTTP_METHOD, constant("GET"))
                .toD(countryApiBaseUrl + "/${body.countryIso2}?bridgeEndpoint=true&throwExceptionOnFailure=false")
                .choice()
                    .when(header(Exchange.HTTP_RESPONSE_CODE).isEqualTo(200))
                        .bean(countryResponseMapper, "enrichOrder(${body}, ${exchangeProperty.originalOrder})")
                    .otherwise()
                        .log(LoggingLevel.WARN, "No se pudo obtener enriquecimiento de pais para ${exchangeProperty.originalOrder.countryIso2} (HTTP ${header.CamelHttpResponseCode})")
                        .bean(countryResponseMapper, "enrichOrder(null, ${exchangeProperty.originalOrder})")
                .end();
    }
}