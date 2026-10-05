package com.fluxaria.middleware.orchestration.service;

import com.fluxaria.middleware.domain.model.BatchSummary;
import com.fluxaria.middleware.domain.model.Order;
import com.fluxaria.middleware.shared.logging.CorrelationIdProcessor;
import com.fluxaria.middleware.shared.model.ErrorResponse;
import org.apache.camel.AggregationStrategy;
import org.apache.camel.Exchange;
import org.springframework.stereotype.Component;

/**
 * Servicio de Agregación para Procesamiento de Lotes (EIP Aggregator).
 * Reside en la capa de Orquestación / Aplicación como servicio de proceso en memoria.
 * Coordina la acumulación de métricas de ejecución sobre el modelo de dominio BatchSummary
 * a medida que el Splitter procesa cada pedido del lote.
 */
@Component
public class BatchAggregationService implements AggregationStrategy {

    @Override
    public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
        BatchSummary summary;

        if (oldExchange == null) {
            summary = new BatchSummary();
            String batchId = newExchange.getProperty("batchId", String.class);
            if (batchId == null || batchId.isBlank()) {
                batchId = newExchange.getIn().getHeader("X-Batch-ID", String.class);
            }
            String correlationId = newExchange.getProperty(CorrelationIdProcessor.MDC_CORRELATION_KEY, String.class);
            if (correlationId == null) {
                correlationId = newExchange.getIn().getHeader(CorrelationIdProcessor.CORRELATION_HEADER, String.class);
            }
            Integer totalInBatch = newExchange.getProperty("totalInBatch", Integer.class);

            summary.setBatchId(batchId);
            summary.setCorrelationId(correlationId);
            if (totalInBatch != null) {
                summary.setTotalOrders(totalInBatch);
            }
        } else {
            summary = oldExchange.getIn().getBody(BatchSummary.class);
        }

        Boolean orderFailed = newExchange.getProperty("orderFailed", Boolean.class);
        Exception ex = newExchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
        Integer httpCode = newExchange.getIn().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class);
        Object body = newExchange.getIn().getBody();

        if (Boolean.TRUE.equals(orderFailed) || ex != null || (body instanceof ErrorResponse) || (httpCode != null && httpCode >= 400)) {
            summary.incrementFailed();
        } else if (body instanceof Order order) {
            summary.incrementProcessed();
            summary.addTotalEur(order.getTotalEur());
        } else {
            summary.incrementFailed();
        }

        if (summary.getTotalOrders() < (summary.getProcessedCount() + summary.getFailedCount())) {
            summary.setTotalOrders(summary.getProcessedCount() + summary.getFailedCount());
        }

        if (oldExchange == null) {
            newExchange.getIn().setBody(summary);
            return newExchange;
        }

        oldExchange.getIn().setBody(summary);
        return oldExchange;
    }
}
