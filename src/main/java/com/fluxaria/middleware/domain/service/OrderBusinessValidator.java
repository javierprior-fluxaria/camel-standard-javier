package com.fluxaria.middleware.domain.service;

import com.fluxaria.middleware.domain.model.Order;
import com.fluxaria.middleware.domain.model.OrderItem;
import com.fluxaria.middleware.shared.error.BusinessValidationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Validador de Negocio Puro.
 * Reglas de negocio:
 * 1. Cliente presente.
 * 2. Código de país ISO-2 existente (ISO 3166-1 alpha-2).
 * 3. Moneda ISO-4217 válida y reconocida.
 * 4. Líneas de pedido no vacías, con cantidades > 0 y precios unitarios > 0.
 *
 * Si falla, lanza BusinessValidationException que se traduce a HTTP 400 sin invocar sistemas externos.
 */
@Service
public class OrderBusinessValidator {

    private static final Set<String> ISO_COUNTRIES = Set.of(Locale.getISOCountries());

    public Order validate(Order order) {
        if (order == null) {
            throw new BusinessValidationException("El pedido no puede ser nulo");
        }

        List<String> errors = new ArrayList<>();

        // 1. Cliente
        if (order.getCustomerId() == null || order.getCustomerId().isBlank()) {
            errors.add("El campo 'customerId' es obligatorio");
        }

        // 2. País ISO-2
        if (order.getCountryIso2() == null || order.getCountryIso2().isBlank()) {
            errors.add("El campo 'country' es obligatorio");
        } else {
            String upperCountry = order.getCountryIso2().toUpperCase();
            if (!ISO_COUNTRIES.contains(upperCountry)) {
                errors.add("El país '" + order.getCountryIso2() + "' no es un código ISO-2 válido");
            }
        }

        // 3. Moneda ISO-4217
        if (order.getCurrency() == null || order.getCurrency().isBlank()) {
            errors.add("El campo 'currency' es obligatorio");
        } else {
            try {
                Currency.getInstance(order.getCurrency().toUpperCase());
            } catch (IllegalArgumentException e) {
                errors.add("La moneda '" + order.getCurrency() + "' no es un código ISO-4217 válido");
            }
        }

        // 4. Líneas del pedido
        if (order.getItems() == null || order.getItems().isEmpty()) {
            errors.add("El pedido debe contener al menos una línea de producto");
        } else {
            for (int i = 0; i < order.getItems().size(); i++) {
                OrderItem item = order.getItems().get(i);
                int lineNum = i + 1;
                if (item.productId() == null || item.productId().isBlank()) {
                    errors.add("Línea " + lineNum + ": 'productId' es obligatorio");
                }
                if (item.quantity() <= 0) {
                    errors.add("Línea " + lineNum + ": la cantidad debe ser mayor que 0 (recibido: " + item.quantity() + ")");
                }
                if (item.unitPrice() == null || item.unitPrice().compareTo(BigDecimal.ZERO) <= 0) {
                    errors.add("Línea " + lineNum + ": el precio unitario debe ser mayor que 0 (recibido: " + item.unitPrice() + ")");
                }
            }
        }

        if (!errors.isEmpty()) {
            throw new BusinessValidationException(
                    "Error de validación del pedido: se encontraron " + errors.size() + " errores de regla de negocio",
                    errors
            );
        }

        order.markValidated();
        return order;
    }
}