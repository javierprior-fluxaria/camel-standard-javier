package com.fluxaria.middleware.domain.model;

import java.time.Instant;

/**
 * Evento de dominio para auditoría y publicación en Kafka.
 */
public record OrderEvent(
        String orderId,
        String eventType,
        String payload,
        String correlationId,
        Instant createdAt
) {
    public static OrderEvent of(String orderId, String eventType, String payload, String correlationId) {
        return new OrderEvent(orderId, eventType, payload, correlationId, Instant.now());
    }
}