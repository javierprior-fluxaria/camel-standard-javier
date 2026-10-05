package com.fluxaria.middleware.inbound.kafka.mapper;

import com.fluxaria.middleware.domain.model.Order;
import com.fluxaria.middleware.inbound.kafka.dto.OrderBatchItemDto;
import com.fluxaria.middleware.inbound.kafka.dto.OrderBatchItemProductDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Tests Unitarios de Inbound Kafka: OrderBatchInboundMapper")
class OrderBatchInboundMapperTest {

    private final OrderBatchInboundMapper mapper = new OrderBatchInboundMapper();

    @Test
    @DisplayName("Debe mapear array de DTOs a lista de entidades de dominio Order")
    void testToDomainList() {
        OrderBatchItemProductDto product = new OrderBatchItemProductDto("P1", 2, new BigDecimal("25.00"));
        OrderBatchItemDto item1 = new OrderBatchItemDto("ORD-1", "CUST-1", "ES", "EUR", List.of(product));
        OrderBatchItemDto item2 = new OrderBatchItemDto("ORD-2", "CUST-2", "FR", "EUR", List.of(product));

        OrderBatchItemDto[] array = new OrderBatchItemDto[]{item1, item2};

        List<Order> orders = mapper.toDomainList(array);

        assertNotNull(orders);
        assertEquals(2, orders.size());
        assertEquals("ORD-1", orders.get(0).getOrderId());
        assertEquals("CUST-1", orders.get(0).getCustomerId());
        assertEquals("ES", orders.get(0).getCountryIso2());
        assertEquals("EUR", orders.get(0).getCurrency());
        assertEquals(new BigDecimal("50.00"), orders.get(0).getTotalOriginal());

        assertEquals("ORD-2", orders.get(1).getOrderId());
    }

    @Test
    @DisplayName("Debe generar orderId si viene nulo o vacio")
    void testToDomainGeneratesOrderId() {
        OrderBatchItemDto item = new OrderBatchItemDto(null, "CUST-1", "ES", "EUR", List.of());

        Order order = mapper.toDomain(item);

        assertNotNull(order);
        assertNotNull(order.getOrderId());
        assertTrue(order.getOrderId().startsWith("ORD-"));
    }

    @Test
    @DisplayName("Debe retornar lista vacia ante array nulo")
    void testToDomainListNull() {
        assertTrue(mapper.toDomainList(null).isEmpty());
    }
}
