package com.fluxaria.middleware.shared.logging;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Gestiona el identificador de correlacion (X-Correlation-ID y traceparent).
 * Lo inyecta en cabeceras de Camel y en el MDC de SLF4J / Log4j2 para observabilidad en todos los logs.
 */
@Component
public class CorrelationIdProcessor implements Processor {

    public static final String CORRELATION_HEADER = "X-Correlation-ID";
    public static final String MDC_CORRELATION_KEY = "correlationId";

    @Override
    public void process(Exchange exchange) {
        String correlationId = exchange.getIn().getHeader(CORRELATION_HEADER, String.class);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
            exchange.getIn().setHeader(CORRELATION_HEADER, correlationId);
        }
        MDC.put(MDC_CORRELATION_KEY, correlationId);
        exchange.setProperty(MDC_CORRELATION_KEY, correlationId);
    }
}