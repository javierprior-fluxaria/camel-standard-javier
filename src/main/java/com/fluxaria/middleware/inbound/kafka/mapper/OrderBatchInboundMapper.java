package com.fluxaria.middleware.inbound.kafka.mapper;

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

    public List<Order> toDomainList(OrderBatchItemDto[] dtos) {
        if (dtos == null) {
            return Collections.emptyList();
        }
        return Arrays.stream(dtos)
                .map(this::toDomain)
                .toList();
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
