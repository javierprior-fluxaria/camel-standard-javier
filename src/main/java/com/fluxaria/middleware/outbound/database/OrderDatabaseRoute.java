package com.fluxaria.middleware.outbound.database;

import com.fluxaria.middleware.domain.model.Order;
import com.fluxaria.middleware.shared.error.BaseRouteBuilder;
import org.apache.camel.LoggingLevel;
import org.springframework.stereotype.Component;

/**
 * Adaptador Outbound para persistencia en Base de Datos (PostgreSQL).
 */
@Component
public class OrderDatabaseRoute extends BaseRouteBuilder {

    public static final String DIRECT_SAVE_INITIAL = "direct:outbound.database.save-initial";
    public static final String DIRECT_SAVE_FINAL = "direct:outbound.database.save-final";

    @Override
    public void setupRoutes() {
        from(DIRECT_SAVE_INITIAL)
                .routeId("outbound.database.save-initial")
                .log(LoggingLevel.INFO, "Persistiendo estado inicial ENRICHED en PostgreSQL para pedido ${body.orderId}")
                // Se completara la ejecucion SQL contra PostgreSQL en la fase de integracion externa
                ;

        from(DIRECT_SAVE_FINAL)
                .routeId("outbound.database.save-final")
                .log(LoggingLevel.INFO, "Actualizando estado final CONFIRMED en PostgreSQL para pedido ${body.orderId}")
                // Se completara la ejecucion SQL contra PostgreSQL en la fase de integracion externa
                ;
    }
}