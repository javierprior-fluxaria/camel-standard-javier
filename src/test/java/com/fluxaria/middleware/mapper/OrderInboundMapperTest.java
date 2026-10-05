package com.fluxaria.middleware.mapper;

import com.fluxaria.middleware.domain.model.CountryDetails;
import com.fluxaria.middleware.domain.model.Order;
import com.fluxaria.middleware.domain.model.OrderStatus;
import com.fluxaria.middleware.inbound.rest.dto.OrderItemDto;
import com.fluxaria.middleware.inbound.rest.dto.OrderRequestDto;
import com.fluxaria.middleware.inbound.rest.dto.OrderResponseDto;
import com.fluxaria.middleware.inbound.rest.mapper.OrderInboundMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Tests Unitarios de Mappers: OrderInboundMapper")
class OrderInboundMapperTest {

    private OrderInboundMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new OrderInboundMapper();
    }

    @Test
    @DisplayName("Debe mapear correctamente OrderRequestDto a Order de Dominio")
    void testToDomain() {
        OrderItemDto itemDto = new OrderItemDto("ITEM-1", 3, new BigDecimal("25.00"));
        OrderRequestDto requestDto = new OrderRequestDto(
                "ORD-CUSTOM-01",
                "CUST-OK",
                "es",
                "eur",
                List.of(itemDto)
        );

        Order domainOrder = mapper.toDomain(requestDto);

        assertNotNull(domainOrder);
        assertEquals("ORD-CUSTOM-01", domainOrder.getOrderId());
        assertEquals("CUST-OK", domainOrder.getCustomerId());
        assertEquals("ES", domainOrder.getCountryIso2());
        assertEquals("EUR", domainOrder.getCurrency());
        assertEquals(1, domainOrder.getItems().size());
        assertEquals(new BigDecimal("75.00"), domainOrder.getTotalOriginal());
        assertEquals(OrderStatus.RECEIVED, domainOrder.getStatus());
    }

    @Test
    @DisplayName("Debe generar un orderId si no viene informado en el DTO")
    void testToDomainGeneratesOrderIdIfMissing() {
        OrderRequestDto requestDto = new OrderRequestDto(
                null,
                "CUST-OK",
                "FR",
                "EUR",
                List.of(new OrderItemDto("ITEM-1", 1, new BigDecimal("10.00")))
        );

        Order domainOrder = mapper.toDomain(requestDto);

        assertNotNull(domainOrder.getOrderId());
        assertTrue(domainOrder.getOrderId().startsWith("ORD-"));
    }

    @Test
    @DisplayName("Debe mapear Order de Dominio a OrderResponseDto")
    void testToResponseDto() {
        OrderItemDto itemDto = new OrderItemDto("ITEM-1", 2, new BigDecimal("50.00"));
        OrderRequestDto requestDto = new OrderRequestDto("ORD-100", "CUST-OK", "US", "USD", List.of(itemDto));
        Order domainOrder = mapper.toDomain(requestDto);

        domainOrder.enrichCountry(new CountryDetails("United States of America", "Americas", "USD", "+1"));
        domainOrder.applyExchangeRate(new BigDecimal("0.920000"));
        domainOrder.markConfirmed();

        OrderResponseDto responseDto = mapper.toResponseDto(domainOrder);

        assertNotNull(responseDto);
        assertEquals("ORD-100", responseDto.orderId());
        assertEquals(OrderStatus.CONFIRMED, responseDto.status());
        assertEquals(new BigDecimal("100.00"), responseDto.totalOriginal());
        assertEquals(new BigDecimal("92.00"), responseDto.totalEur());
        assertEquals(new BigDecimal("0.920000"), responseDto.exchangeRate());
        assertEquals("United States of America", responseDto.countryName());
        assertEquals("+1", responseDto.phonePrefix());
    }
}