package com.fluxaria.middleware.inbound.kafka.mapper;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fluxaria.middleware.domain.model.Order;
import com.fluxaria.middleware.domain.model.OrderItem;
import com.fluxaria.middleware.inbound.kafka.dto.OrderBatchItemDto;
import com.fluxaria.middleware.inbound.kafka.dto.OrderBatchItemProductDto;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Traductor explícito entre contratos DTO de Inbound Kafka y el Modelo Canónico de Dominio (Order).
 * Desacopla completamente el contrato del topic orders.batch.in del modelo de dominio interno.
 */
@Component
public class OrderBatchInboundMapper {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public List<Order> toDomainList(OrderBatchItemDto[] dtos) {
        if (dtos == null) {
            return Collections.emptyList();
        }
        return Arrays.stream(dtos)
                .map(this::toDomain)
                .toList();
    }

    public List<Order> toDomainList(Object body) {
        if (body == null) {
            return Collections.emptyList();
        }
        if (body instanceof OrderBatchItemDto[] dtos) {
            return toDomainList(dtos);
        }

        try {
            JsonNode root;
            if (body instanceof String s) {
                root = objectMapper.readTree(s);
            } else if (body instanceof byte[] bytes) {
                root = objectMapper.readTree(bytes);
            } else {
                root = objectMapper.valueToTree(body);
            }

            JsonNode arrayNode = root;
            if (root.isObject()) {
                if (root.has("value") && root.get("value").isArray()) {
                    arrayNode = root.get("value");
                } else if (root.has("orders") && root.get("orders").isArray()) {
                    arrayNode = root.get("orders");
                }
            }

            if (!arrayNode.isArray()) {
                throw new IllegalArgumentException("Payload de lote invalido: se esperaba un array JSON o un objeto con propiedad 'value'/'orders'");
            }

            OrderBatchItemDto[] dtos = objectMapper.treeToValue(arrayNode, OrderBatchItemDto[].class);
            return toDomainList(dtos);
        } catch (Exception e) {
            if (e instanceof RuntimeException re) {
                throw re;
            }
            throw new IllegalArgumentException("Error al procesar payload de lote: " + e.getMessage(), e);
        }
    }

    public Order toDomain(OrderBatchItemDto dto) {
        if (dto == null) {
            return null;
        }

        String orderId = (dto.orderId() != null && !dto.orderId().isBlank())
                ? dto.orderId()
                : "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        List<OrderItem> domainItems = (dto.items() != null)
                ? dto.items().stream().map(this::toDomainItem).toList()
                : Collections.emptyList();

        return Order.createNew(
                orderId,
                dto.customerId() != null ? dto.customerId() : "",
                dto.country() != null ? dto.country().trim().toUpperCase() : "",
                dto.currency() != null ? dto.currency().trim().toUpperCase() : "",
                domainItems
        );
    }

    public OrderItem toDomainItem(OrderBatchItemProductDto dto) {
        if (dto == null) {
            return null;
        }
        return OrderItem.of(dto.productId(), dto.quantity(), dto.unitPrice());
    }
}
