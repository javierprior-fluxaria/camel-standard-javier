package com.fluxaria.middleware.mapper;

import com.fluxaria.middleware.domain.model.CountryDetails;
import com.fluxaria.middleware.domain.model.Order;
import com.fluxaria.middleware.domain.model.OrderItem;
import com.fluxaria.middleware.domain.model.OrderStatus;
import com.fluxaria.middleware.outbound.database.mapper.OrderDatabaseMapper;
import com.fluxaria.middleware.outbound.erp.dto.LegacyErpOrderDto;
import com.fluxaria.middleware.outbound.erp.mapper.LegacyErpMapper;
import com.fluxaria.middleware.outbound.kafka.dto.OrderKafkaEventDto;
import com.fluxaria.middleware.outbound.kafka.mapper.OrderKafkaMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Tests Unitarios de Mappers Outbound: Database, Kafka y ERP")
class OutboundMappersTest {

    @Test
    @DisplayName("OrderDatabaseMapper: mapea a parametros de INSERT y UPDATE")
    void testOrderDatabaseMapper() {
        OrderDatabaseMapper dbMapper = new OrderDatabaseMapper();
        Order order = Order.createNew("ORD-TEST-01", "CUST-01", "ES", "EUR", List.of(
                OrderItem.of("PROD-1", 2, new BigDecimal("10.00"))
        ));
        order.enrichCountry(new CountryDetails("Spain", "Europe", "EUR", "+34"));
        order.applyExchangeRate(BigDecimal.ONE);

        Map<String, Object> insertParams = dbMapper.toInsertOrderParams(order);
        assertEquals("ORD-TEST-01", insertParams.get("orderId"));
        assertEquals("CUST-01", insertParams.get("customerId"));
        assertEquals("ES", insertParams.get("countryIso2"));
        assertEquals("Spain", insertParams.get("countryName"));
        assertEquals("ENRICHED", insertParams.get("status"));

        Map<String, Object> auditParams = dbMapper.toInitialAuditParams(order, "corr-123");
        assertEquals("ORD-TEST-01", auditParams.get("orderId"));
        assertEquals("ORDER_ENRICHED", auditParams.get("eventType"));
        assertEquals("corr-123", auditParams.get("correlationId"));

        order.markAsConfirmedByErp();
        Map<String, Object> updateParams = dbMapper.toUpdateStatusParams(order);
        assertEquals("CONFIRMED", updateParams.get("status"));

        Map<String, Object> confirmedAudit = dbMapper.toConfirmedAuditParams(order, "corr-123");
        assertEquals("ORDER_CONFIRMED", confirmedAudit.get("eventType"));
    }

    @Test
    @DisplayName("OrderKafkaMapper: mapea Order a OrderKafkaEventDto")
    void testOrderKafkaMapper() {
        OrderKafkaMapper kafkaMapper = new OrderKafkaMapper();
        Order order = Order.createNew("ORD-TEST-02", "CUST-02", "FR", "EUR", List.of(
                OrderItem.of("PROD-2", 1, new BigDecimal("50.00"))
        ));
        order.applyExchangeRate(BigDecimal.ONE);
        order.markAsConfirmedByErp();

        OrderKafkaEventDto eventDto = kafkaMapper.toEventDto(order, "corr-456");
        assertNotNull(eventDto);
        assertEquals("OrderProcessed", eventDto.eventType());
        assertEquals("ORD-TEST-02", eventDto.orderId());
        assertEquals("CONFIRMED", eventDto.status());
        assertEquals("corr-456", eventDto.correlationId());
        assertEquals(new BigDecimal("50.00"), eventDto.totalEur());
    }

    @Test
    @DisplayName("LegacyErpMapper: mapea a LegacyErpOrderDto y transiciona estado con handleSuccess")
    void testLegacyErpMapper() {
        LegacyErpMapper erpMapper = new LegacyErpMapper();
        Order order = Order.createNew("ORD-TEST-03", "CUST-03", "IT", "EUR", List.of(
                OrderItem.of("PROD-3", 1, new BigDecimal("30.00"))
        ));
        order.applyExchangeRate(BigDecimal.ONE);

        LegacyErpOrderDto dto = erpMapper.toLegacyDto(order);
        assertNotNull(dto);
        assertEquals("ORD-TEST-03", dto.legacyOrderId());
        assertEquals(1, dto.lines().size());

        Order confirmedOrder = erpMapper.handleSuccess(order);
        assertEquals(OrderStatus.CONFIRMED, confirmedOrder.getStatus());
    }

    @Test
    @DisplayName("OrderKafkaMapper: mapea BatchSummary a OrderBatchSummaryEventDto")
    void testBatchSummaryEventDto() {
        OrderKafkaMapper kafkaMapper = new OrderKafkaMapper();
        com.fluxaria.middleware.domain.model.BatchSummary summary = new com.fluxaria.middleware.domain.model.BatchSummary(
                "BATCH-01", 5, 4, 1, new BigDecimal("250.00"), "corr-789"
        );

        var dto = kafkaMapper.toBatchSummaryEventDto(summary);
        assertNotNull(dto);
        assertEquals("BATCH-01", dto.batchId());
        assertEquals(5, dto.totalOrders());
        assertEquals(4, dto.processedCount());
        assertEquals(1, dto.failedCount());
        assertEquals(new BigDecimal("250.00"), dto.totalEur());
        assertEquals("corr-789", dto.correlationId());
        assertNotNull(dto.processedAt());
    }
}
