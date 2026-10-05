package com.fluxaria.middleware.domain;

import com.fluxaria.middleware.domain.model.Order;
import com.fluxaria.middleware.domain.model.OrderItem;
import com.fluxaria.middleware.domain.model.OrderStatus;
import com.fluxaria.middleware.domain.service.OrderBusinessValidator;
import com.fluxaria.middleware.shared.error.BusinessValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Tests Unitarios de Dominio: OrderBusinessValidator")
class OrderBusinessValidatorTest {

    private OrderBusinessValidator validator;

    @BeforeEach
    void setUp() {
        validator = new OrderBusinessValidator();
    }

    @Test
    @DisplayName("Debe validar correctamente un pedido valido y cambiar su estado a VALIDATED")
    void testValidOrderPasses() {
        Order order = Order.createNew(
                "ORD-123",
                "CUST-OK",
                "ES",
                "EUR",
                List.of(OrderItem.of("PROD-1", 2, new BigDecimal("15.50")))
        );

        Order validated = validator.validate(order);

        assertNotNull(validated);
        assertEquals(OrderStatus.VALIDATED, validated.getStatus());
        assertEquals(new BigDecimal("31.00"), validated.getTotalOriginal());
    }

    @Test
    @DisplayName("Debe rechazar un pedido con pais ISO-2 no existente")
    void testInvalidCountryFails() {
        Order order = Order.createNew(
                "ORD-123",
                "CUST-OK",
                "XX", // Pais inexistente
                "EUR",
                List.of(OrderItem.of("PROD-1", 1, new BigDecimal("10.00")))
        );

        BusinessValidationException ex = assertThrows(BusinessValidationException.class, () -> validator.validate(order));
        assertTrue(ex.getValidationErrors().stream().anyMatch(e -> e.contains("no es un código ISO-2 válido")));
    }

    @Test
    @DisplayName("Debe rechazar un pedido con moneda ISO-4217 invalida")
    void testInvalidCurrencyFails() {
        Order order = Order.createNew(
                "ORD-123",
                "CUST-OK",
                "FR",
                "XYZ", // Moneda invalida
                List.of(OrderItem.of("PROD-1", 1, new BigDecimal("10.00")))
        );

        BusinessValidationException ex = assertThrows(BusinessValidationException.class, () -> validator.validate(order));
        assertTrue(ex.getValidationErrors().stream().anyMatch(e -> e.contains("no es un código ISO-4217 válido")));
    }

    @Test
    @DisplayName("Debe rechazar un pedido sin lineas o con cantidad <= 0")
    void testInvalidItemsFail() {
        Order orderNoItems = Order.createNew("ORD-1", "CUST-OK", "DE", "EUR", Collections.emptyList());
        assertThrows(BusinessValidationException.class, () -> validator.validate(orderNoItems));

        Order orderNegativeQuantity = Order.createNew(
                "ORD-2",
                "CUST-OK",
                "DE",
                "EUR",
                List.of(OrderItem.of("PROD-1", 0, new BigDecimal("10.00")))
        );
        BusinessValidationException ex = assertThrows(BusinessValidationException.class, () -> validator.validate(orderNegativeQuantity));
        assertTrue(ex.getValidationErrors().stream().anyMatch(e -> e.contains("la cantidad debe ser mayor que 0")));
    }
}