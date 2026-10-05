package com.fluxaria.middleware.outbound.database;

import com.fluxaria.middleware.outbound.database.mapper.OrderDatabaseMapper;
import com.fluxaria.middleware.shared.error.BaseRouteBuilder;
import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Adaptador Outbound para persistencia en PostgreSQL con camel-sql.
 * Gestiona las operaciones en las tablas `orders` y `order_events`.
 */
@Component
public class OrderDatabaseRoute extends BaseRouteBuilder {

    public static final String DIRECT_SAVE_INITIAL = "direct:outbound.database.save-initial";
    public static final String DIRECT_SAVE_FINAL = "direct:outbound.database.save-final";

    @Autowired
    private OrderDatabaseMapper orderDatabaseMapper;

    @Override
    public void setupRoutes() {

        // 1. Guardar estado inicial (ENRICHED)
        from(DIRECT_SAVE_INITIAL)
                .routeId("outbound.database.save-initial")
                .log(LoggingLevel.INFO, "Persistiendo estado inicial ENRICHED en PostgreSQL para pedido ${body.orderId}")
                .setProperty("currentOrder", body())
                .bean(orderDatabaseMapper, "toInsertOrderParams")
                .to("sql:INSERT INTO orders (order_id, customer_id, country_iso2, currency, total_original, total_eur, exchange_rate, country_name, country_region, phone_prefix, status) " +
                        "VALUES (:#orderId, :#customerId, :#countryIso2, :#currency, :#totalOriginal, :#totalEur, :#exchangeRate, :#countryName, :#countryRegion, :#phonePrefix, :#status)")
                // Insertar evento de auditoria inicial
                .bean(orderDatabaseMapper, "toInitialAuditParams")
                .to("sql:INSERT INTO order_events (order_id, event_type, payload, correlation_id) " +
                        "VALUES (:#orderId, :#eventType, :#payload, :#correlationId)")
                // Restaurar el objeto Order como Body usando Camel DSL nativo
                .setBody(exchangeProperty("currentOrder"));

        // 2. Actualizar estado final (CONFIRMED)
        from(DIRECT_SAVE_FINAL)
                .routeId("outbound.database.save-final")
                .log(LoggingLevel.INFO, "Actualizando estado final CONFIRMED en PostgreSQL para pedido ${body.orderId}")
                .setProperty("currentOrder", body())
                .bean(orderDatabaseMapper, "toUpdateStatusParams")
                .to("sql:UPDATE orders SET status = :#status, updated_at = CURRENT_TIMESTAMP WHERE order_id = :#orderId")
                // Insertar evento de confirmacion
                .bean(orderDatabaseMapper, "toConfirmedAuditParams")
                .to("sql:INSERT INTO order_events (order_id, event_type, payload, correlation_id) " +
                        "VALUES (:#orderId, :#eventType, :#payload, :#correlationId)")
                // Restaurar el objeto Order como Body usando Camel DSL nativo
                .setBody(exchangeProperty("currentOrder"));
    }
}