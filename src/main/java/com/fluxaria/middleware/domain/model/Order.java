package com.fluxaria.middleware.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Agregado raíz del dominio para el pedido internacional.
 * Modelo canónico puro, desacoplado de protocolos de transporte y persistencia.
 */
public class Order {

    private final String orderId;
    private final String customerId;
    private final String countryIso2;
    private final String currency;
    private final List<OrderItem> items;
    private final BigDecimal totalOriginal;
    private BigDecimal totalEur;
    private BigDecimal exchangeRate;
    private CountryDetails countryDetails;
    private OrderStatus status;
    private final Instant createdAt;
    private Instant updatedAt;

    public Order(String orderId,
                 String customerId,
                 String countryIso2,
                 String currency,
                 List<OrderItem> items,
                 BigDecimal totalOriginal,
                 BigDecimal totalEur,
                 BigDecimal exchangeRate,
                 CountryDetails countryDetails,
                 OrderStatus status,
                 Instant createdAt,
                 Instant updatedAt) {
        this.orderId = Objects.requireNonNull(orderId, "orderId cannot be null");
        this.customerId = Objects.requireNonNull(customerId, "customerId cannot be null");
        this.countryIso2 = Objects.requireNonNull(countryIso2, "countryIso2 cannot be null");
        this.currency = Objects.requireNonNull(currency, "currency cannot be null");
        this.items = items != null ? List.copyOf(items) : Collections.emptyList();
        this.totalOriginal = totalOriginal != null ? totalOriginal : calculateCalculatedTotal();
        this.totalEur = totalEur;
        this.exchangeRate = exchangeRate;
        this.countryDetails = countryDetails != null ? countryDetails : CountryDetails.empty();
        this.status = status != null ? status : OrderStatus.RECEIVED;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : this.createdAt;
    }

    public static Order createNew(String orderId, String customerId, String countryIso2, String currency, List<OrderItem> items) {
        BigDecimal total = items != null
                ? items.stream().map(OrderItem::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add)
                : BigDecimal.ZERO;
        Instant now = Instant.now();
        return new Order(orderId, customerId, countryIso2, currency, items, total, null, null, CountryDetails.empty(), OrderStatus.RECEIVED, now, now);
    }

    private BigDecimal calculateCalculatedTotal() {
        return items.stream()
                .map(OrderItem::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // --- Métodos de transición de estado funcional del Dominio ---

    public void markValidated() {
        this.status = OrderStatus.VALIDATED;
        this.updatedAt = Instant.now();
    }

    public void enrichCountry(CountryDetails countryDetails) {
        this.countryDetails = Objects.requireNonNull(countryDetails, "countryDetails cannot be null");
        this.updatedAt = Instant.now();
    }

    public void applyCurrencyConversion(BigDecimal exchangeRate, BigDecimal totalEur) {
        this.exchangeRate = Objects.requireNonNull(exchangeRate, "exchangeRate cannot be null");
        this.totalEur = Objects.requireNonNull(totalEur, "totalEur cannot be null");
        this.status = OrderStatus.ENRICHED;
        this.updatedAt = Instant.now();
    }

    public void markSentToErp() {
        this.status = OrderStatus.SENT_TO_ERP;
        this.updatedAt = Instant.now();
    }

    public void markConfirmed() {
        this.status = OrderStatus.CONFIRMED;
        this.updatedAt = Instant.now();
    }

    public void markFailed() {
        this.status = OrderStatus.FAILED;
        this.updatedAt = Instant.now();
    }

    // --- Getters ---

    public String getOrderId() {
        return orderId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getCountryIso2() {
        return countryIso2;
    }

    public String getCurrency() {
        return currency;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public BigDecimal getTotalOriginal() {
        return totalOriginal;
    }

    public BigDecimal getTotalEur() {
        return totalEur;
    }

    public BigDecimal getExchangeRate() {
        return exchangeRate;
    }

    public CountryDetails getCountryDetails() {
        return countryDetails;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public String toString() {
        return "Order{" +
                "orderId='" + orderId + '\'' +
                ", customerId='" + customerId + '\'' +
                ", countryIso2='" + countryIso2 + '\'' +
                ", currency='" + currency + '\'' +
                ", itemsCount=" + items.size() +
                ", totalOriginal=" + totalOriginal +
                ", totalEur=" + totalEur +
                ", status=" + status +
                '}';
    }
}