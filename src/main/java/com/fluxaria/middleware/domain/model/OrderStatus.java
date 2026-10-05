package com.fluxaria.middleware.domain.model;

/**
 * Estados del ciclo de vida del pedido en el dominio canónico.
 */
public enum OrderStatus {
    RECEIVED,
    VALIDATED,
    ENRICHED,
    SENT_TO_ERP,
    CONFIRMED,
    FAILED
}