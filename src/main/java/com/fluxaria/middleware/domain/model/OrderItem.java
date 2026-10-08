package com.fluxaria.middleware.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Línea de detalle del pedido en el dominio canónico.
 * Objeto de valor puro, inmutable y sin dependencias de frameworks externos.
 */
public record OrderItem(
        String productId,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal
) {
    public OrderItem {
        if (subtotal == null) {
            subtotal = unitPrice != null ? unitPrice.multiply(BigDecimal.valueOf(quantity)) : BigDecimal.ZERO;
        }
    }

    public static OrderItem of(String productId, int quantity, BigDecimal unitPrice) {
        BigDecimal subtotal = unitPrice != null ? unitPrice.multiply(BigDecimal.valueOf(quantity)) : BigDecimal.ZERO;
        return new OrderItem(productId, quantity, unitPrice, subtotal);
    }
}