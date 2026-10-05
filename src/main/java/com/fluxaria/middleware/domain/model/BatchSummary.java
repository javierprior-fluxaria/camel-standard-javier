package com.fluxaria.middleware.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Modelo de Dominio puro que representa el balance y consolidacion de un lote de pedidos.
 * Agnóstico a protocolos de transporte y persistencia.
 */
public class BatchSummary {

    private String batchId;
    private int totalOrders;
    private int processedCount;
    private int failedCount;
    private BigDecimal totalEur;
    private final Instant processedAt;
    private String correlationId;

    public BatchSummary() {
        this.totalOrders = 0;
        this.processedCount = 0;
        this.failedCount = 0;
        this.totalEur = BigDecimal.ZERO;
        this.processedAt = Instant.now();
    }

    public BatchSummary(String batchId, int totalOrders, int processedCount, int failedCount, BigDecimal totalEur, String correlationId) {
        this.batchId = batchId;
        this.totalOrders = totalOrders;
        this.processedCount = processedCount;
        this.failedCount = failedCount;
        this.totalEur = totalEur != null ? totalEur : BigDecimal.ZERO;
        this.processedAt = Instant.now();
        this.correlationId = correlationId;
    }

    public void incrementProcessed() {
        this.processedCount++;
    }

    public void incrementFailed() {
        this.failedCount++;
    }

    public void addTotalEur(BigDecimal amount) {
        if (amount != null) {
            this.totalEur = this.totalEur.add(amount);
        }
    }

    public String getBatchId() {
        return batchId;
    }

    public void setBatchId(String batchId) {
        this.batchId = batchId;
    }

    public int getTotalOrders() {
        return totalOrders;
    }

    public void setTotalOrders(int totalOrders) {
        this.totalOrders = totalOrders;
    }

    public int getProcessedCount() {
        return processedCount;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public BigDecimal getTotalEur() {
        return totalEur;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }
}
