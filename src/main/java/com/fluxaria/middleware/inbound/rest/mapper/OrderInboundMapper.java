package com.fluxaria.middleware.inbound.rest.mapper;

import com.fluxaria.middleware.domain.model.Order;
import com.fluxaria.middleware.domain.model.OrderItem;
import com.fluxaria.middleware.inbound.rest.dto.OrderItemDto;
import com.fluxaria.middleware.inbound.rest.dto.OrderRequestDto;
import com.fluxaria.middleware.inbound.rest.dto.OrderResponseDto;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Traductor explícito entre DTOs de la capa Inbound REST y el Modelo Canónico del Dominio.
 */
@Component
public class OrderInboundMapper {

    public Order toDomain(OrderRequestDto dto) {
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
                dto.customerId(),
                dto.country() != null ? dto.country().trim().toUpperCase() : null,
                dto.currency() != null ? dto.currency().trim().toUpperCase() : null,
                domainItems
        );
    }

    public OrderItem toDomainItem(OrderItemDto dto) {
        if (dto == null) {
            return null;
        }
        return OrderItem.of(dto.productId(), dto.quantity(), dto.unitPrice());
    }

    public OrderResponseDto toResponseDto(Order order) {
        if (order == null) {
            return null;
        }
        return new OrderResponseDto(
                order.getOrderId(),
                order.getCustomerId(),
                order.getStatus(),
                order.getTotalOriginal(),
                order.getCurrency(),
                order.getTotalEur(),
                order.getExchangeRate(),
                order.getCountryDetails() != null ? order.getCountryDetails().officialName() : null,
                order.getCountryDetails() != null ? order.getCountryDetails().region() : null,
                order.getCountryDetails() != null ? order.getCountryDetails().phonePrefix() : null,
                order.getUpdatedAt()
        );
    }
}