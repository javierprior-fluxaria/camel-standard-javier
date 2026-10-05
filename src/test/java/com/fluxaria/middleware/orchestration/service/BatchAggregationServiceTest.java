package com.fluxaria.middleware.orchestration.service;

import com.fluxaria.middleware.domain.model.BatchSummary;
import com.fluxaria.middleware.domain.model.CountryDetails;
import com.fluxaria.middleware.domain.model.Order;
import com.fluxaria.middleware.domain.model.OrderItem;
import com.fluxaria.middleware.shared.model.ErrorResponse;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Tests Unitarios de Orquestacion de Lotes: BatchAggregationService")
class BatchAggregationServiceTest {

    private BatchAggregationService service;
    private CamelContext camelContext;

    @BeforeEach
    void setUp() {
        service = new BatchAggregationService();
        camelContext = new DefaultCamelContext();
    }

    @Test
    @DisplayName("Debe agregar correctamente un pedido exitoso y acumular su importe EUR sobre BatchSummary")
    void testAggregateSuccess() {
        Order order1 = Order.createNew("ORD-1", "CUST-1", "ES", "EUR", List.of(OrderItem.of("P1", 1, new BigDecimal("50.00"))));
        order1.enrichCountry(new CountryDetails("Spain", "Europe", "EUR", "+34"));
        order1.applyExchangeRate(BigDecimal.ONE);

        Exchange ex1 = new DefaultExchange(camelContext);
        ex1.getIn().setBody(order1);
        ex1.setProperty("batchId", "BATCH-TEST-01");
        ex1.setProperty("totalInBatch", 2);

        Exchange aggregated = service.aggregate(null, ex1);

        assertNotNull(aggregated);
        BatchSummary summary = aggregated.getIn().getBody(BatchSummary.class);
        assertNotNull(summary);
        assertEquals("BATCH-TEST-01", summary.getBatchId());
        assertEquals(2, summary.getTotalOrders());
        assertEquals(1, summary.getProcessedCount());
        assertEquals(0, summary.getFailedCount());
        assertEquals(new BigDecimal("50.00"), summary.getTotalEur());
    }

    @Test
    @DisplayName("Debe agregar pedidos mixtos (exitosos y fallidos)")
    void testAggregateMixedOrders() {
        Order order1 = Order.createNew("ORD-1", "CUST-1", "ES", "EUR", List.of(OrderItem.of("P1", 1, new BigDecimal("100.00"))));
        order1.applyExchangeRate(BigDecimal.ONE);

        Exchange ex1 = new DefaultExchange(camelContext);
        ex1.getIn().setBody(order1);
        ex1.setProperty("batchId", "BATCH-MIXED");
        ex1.setProperty("totalInBatch", 2);

        // Pedido 1 OK
        Exchange state = service.aggregate(null, ex1);

        // Pedido 2 Falla
        Exchange ex2 = new DefaultExchange(camelContext);
        ex2.setProperty("orderFailed", true);
        ex2.getIn().setBody(ErrorResponse.of("type", "Error", 400, "Validation failed", "/path", "corr-1"));

        state = service.aggregate(state, ex2);

        BatchSummary summary = state.getIn().getBody(BatchSummary.class);
        assertNotNull(summary);
        assertEquals("BATCH-MIXED", summary.getBatchId());
        assertEquals(2, summary.getTotalOrders());
        assertEquals(1, summary.getProcessedCount());
        assertEquals(1, summary.getFailedCount());
        assertEquals(new BigDecimal("100.00"), summary.getTotalEur());
    }
}
