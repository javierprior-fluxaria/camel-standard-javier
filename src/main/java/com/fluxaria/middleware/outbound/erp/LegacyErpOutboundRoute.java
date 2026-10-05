package com.fluxaria.middleware.outbound.erp;

import com.fluxaria.middleware.domain.model.Order;
import com.fluxaria.middleware.shared.error.BaseRouteBuilder;
import org.apache.camel.LoggingLevel;
import org.springframework.stereotype.Component;

/**
 * Adaptador Outbound hacia el Legacy ERP.
 */
@Component
public class LegacyErpOutboundRoute extends BaseRouteBuilder {

    public static final String DIRECT_SUBMIT = "direct:outbound.erp.submit";

    @Override
    public void setupRoutes() {
        from(DIRECT_SUBMIT)
                .routeId("outbound.erp.submit")
                .log(LoggingLevel.INFO, "Enviando pedido ${body.orderId} al Legacy ERP...")
                .process(exchange -> {
                    Order order = exchange.getIn().getBody(Order.class);
                    order.markSentToErp();
                    order.markConfirmed();
                });
    }
}