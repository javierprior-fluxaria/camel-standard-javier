package com.fluxaria.middleware.outbound.currency;

import com.fluxaria.middleware.outbound.currency.mapper.CurrencyConversionMapper;
import com.fluxaria.middleware.shared.error.BaseRouteBuilder;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Adaptador Outbound para la conversion de divisa a EUR:
 * https://open.er-api.com/v6/latest/{moneda} (o https://api.frankfurter.app/latest?from={moneda}&to=EUR)
 */
@Component
public class CurrencyConversionRoute extends BaseRouteBuilder {

    public static final String ROUTE_ID = "outbound.currency.convert";
    public static final String DIRECT_CONVERT = "direct:" + ROUTE_ID;

    @Value("${integration.outbound.currency.base-url:https://open.er-api.com/v6/latest}")
    private String currencyApiBaseUrl;

    @Autowired
    private CurrencyConversionMapper currencyConversionMapper;

    @Override
    public void setupRoutes() {
        from(DIRECT_CONVERT)
                .routeId(ROUTE_ID)
                .setProperty("originalOrder", body())
                .choice()
                    .when(simple("${body.currency} == 'EUR'"))
                        .log(LoggingLevel.INFO, "El pedido ${body.orderId} ya esta en EUR. Aplicando tasa paritaria 1.0.")
                        .bean(currencyConversionMapper, "applyRate(null, ${exchangeProperty.originalOrder})")
                    .otherwise()
                        .log(LoggingLevel.INFO, "Consultando tasa de cambio para divisa ${body.currency} a EUR...")
                        .setHeader(Exchange.HTTP_METHOD, constant("GET"))
                        .toD(currencyApiBaseUrl + "/${body.currency}?bridgeEndpoint=true&throwExceptionOnFailure=false")
                        .choice()
                            .when(header(Exchange.HTTP_RESPONSE_CODE).isEqualTo(200))
                                .bean(currencyConversionMapper, "applyRate(${body}, ${exchangeProperty.originalOrder})")
                            .otherwise()
                                .log(LoggingLevel.ERROR, "Fallo al obtener conversion de divisa para ${exchangeProperty.originalOrder.currency} (HTTP ${header.CamelHttpResponseCode})")
                                .bean(currencyConversionMapper, "applyRate(null, ${exchangeProperty.originalOrder})")
                        .endChoice()
                .end();
    }
}