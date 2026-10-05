package com.fluxaria.middleware.orchestration;

import com.fluxaria.middleware.domain.service.OrderBusinessValidator;
import com.fluxaria.middleware.outbound.country.CountryEnricherRoute;
import com.fluxaria.middleware.outbound.currency.CurrencyConversionRoute;
import com.fluxaria.middleware.shared.error.BaseRouteBuilder;
import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Orquestador principal del pedido internacional (Capa de Proceso / Aplicacion).
 * Actua como director de orquesta entre el dominio de negocio y los adaptadores outbound.
 */
@Component
public class OrderProcessRoute extends BaseRouteBuilder {

    public static final String ROUTE_ID = "orchestration.order.process";
    public static final String DIRECT_PROCESS = "direct:" + ROUTE_ID;

    @Autowired
    private OrderBusinessValidator businessValidator;

    @Override
    public void setupRoutes() {
        from(DIRECT_PROCESS)
                .routeId(ROUTE_ID)
                .log(LoggingLevel.INFO, "Iniciando orquestacion de negocio para pedido: ${body.orderId}")

                // 2. Validacion de negocio pura (Esquema, ISO pais, ISO moneda, cantidades > 0)
                .bean(businessValidator, "validate")
                .log(LoggingLevel.INFO, "Pedido ${body.orderId} validado funcionalmente con exito")

                // 3. Enriquecimiento de datos de pais (RestCountries)
                .to(CountryEnricherRoute.DIRECT_ENRICH)
                .log(LoggingLevel.INFO, "Pedido ${body.orderId} enriquecido con pais: ${body.countryDetails.officialName}")

                // 4. Conversion de divisa a EUR (ExchangeRate / Frankfurter)
                .to(CurrencyConversionRoute.DIRECT_CONVERT)
                .log(LoggingLevel.INFO, "Pedido ${body.orderId} convertido a EUR: Total EUR=${body.totalEur}, Tasa=${body.exchangeRate}")

                // 5. Persistencia de estado inicial en PostgreSQL (ENRICHED)
                .to("direct:outbound.database.save-initial")

                // 6. Envio al ERP Legacy con Resiliencia (Circuit Breaker + Retries + Timeouts)
                .to("direct:outbound.erp.submit")

                // 7. Persistencia final en PostgreSQL (CONFIRMED)
                .to("direct:outbound.database.save-final")

                // 8. Publicacion del evento en Kafka (orders.processed)
                .to("direct:outbound.kafka.publish")

                .log(LoggingLevel.INFO, "Orquestacion de pedido ${body.orderId} completada exitosamente.");
    }
}