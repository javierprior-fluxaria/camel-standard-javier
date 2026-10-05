package com.fluxaria.middleware.outbound.erp.mapper;

import com.fluxaria.middleware.domain.model.Order;
import com.fluxaria.middleware.outbound.erp.dto.LegacyErpOrderDto;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.List;

/**
 * Mapper Outbound: Traduce el Modelo Canonico Order al DTO tecnico del Legacy ERP.
 */
@Component
public class LegacyErpMapper {

    public LegacyErpOrderDto toLegacyDto(Order order) {
        if (order == null) {
            return null;
        }

        BigDecimal rate = order.getExchangeRate() != null ? order.getExchangeRate() : BigDecimal.ONE;

        List<LegacyErpOrderDto.LegacyErpLineDto> lines = (order.getItems() != null)
                ? order.getItems().stream().map(item -> {
                    BigDecimal unitPriceEur = item.unitPrice().multiply(rate).setScale(2, RoundingMode.HALF_UP);
                    return new LegacyErpOrderDto.LegacyErpLineDto(
                            item.productId(),
                            item.quantity(),
                            unitPriceEur
                    );
                }).toList()
                : Collections.emptyList();

        return new LegacyErpOrderDto(
                order.getOrderId(),
                order.getCustomerId(),
                order.getCountryIso2(),
                order.getTotalEur(),
                lines
        );
    }

    public Order handleSuccess(Order originalOrder) {
        if (originalOrder != null) {
            originalOrder.markAsConfirmedByErp();
        }
        return originalOrder;
    }
}