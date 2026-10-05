package com.fluxaria.middleware.shared.error;

import com.fluxaria.middleware.shared.logging.CorrelationIdProcessor;
import com.fluxaria.middleware.shared.model.ErrorResponse;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.http.base.HttpOperationFailedException;

/**
 * BaseRouteBuilder transversal para estandarizar el manejo de errores segun RFC 7807,
 * reintentos, control de circuitos y logs limpios (sin volcados masivos de stacktraces para errores de negocio).
 */
public abstract class BaseRouteBuilder extends RouteBuilder {

    @Override
    public void configure() throws Exception {

        // 1. Errores funcionales de validacion de negocio -> HTTP 400 Bad Request
        onException(BusinessValidationException.class)
                .handled(true)
                .logHandled(true)
                .logStackTrace(false)
                .log(LoggingLevel.WARN, "Error de validacion de negocio: ${exception.message}")
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(400))
                .setHeader(Exchange.CONTENT_TYPE, constant("application/problem+json"))
                .process(exchange -> {
                    BusinessValidationException ex = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, BusinessValidationException.class);
                    String correlationId = exchange.getProperty(CorrelationIdProcessor.MDC_CORRELATION_KEY, String.class);
                    String path = exchange.getIn().getHeader(Exchange.HTTP_URI, String.class);

                    ErrorResponse response = ErrorResponse.ofValidation(
                            ex.getMessage(),
                            path != null ? path : "/api/v1/orders",
                            correlationId,
                            ex.getValidationErrors()
                    );
                    exchange.getIn().setBody(response);
                });

        // 2. Errores HTTP de backends (409 Conflict, 4xx, etc.)
        onException(HttpOperationFailedException.class)
                .onWhen(exchange -> {
                    HttpOperationFailedException ex = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, HttpOperationFailedException.class);
                    return ex.getStatusCode() == 409;
                })
                .handled(true)
                .logHandled(true)
                .logStackTrace(false)
                .log(LoggingLevel.WARN, "Conflicto de idempotencia / duplicado detectado en backend: ${exception.message}")
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(409))
                .setHeader(Exchange.CONTENT_TYPE, constant("application/problem+json"))
                .process(exchange -> {
                    String correlationId = exchange.getProperty(CorrelationIdProcessor.MDC_CORRELATION_KEY, String.class);
                    ErrorResponse response = ErrorResponse.of(
                            "https://api.fluxaria.com/errors/conflict",
                            "Duplicate Resource Conflict",
                            409,
                            "El pedido ya existe en el sistema destino o ha sido procesado previamente.",
                            "/api/v1/orders",
                            correlationId
                    );
                    exchange.getIn().setBody(response);
                });

        // 3. Excepciones genericas no controladas -> HTTP 500 / 502
        onException(Exception.class)
                .handled(true)
                .logHandled(true)
                .log(LoggingLevel.ERROR, "Error inesperado en flujo middleware: ${exception.message}")
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(500))
                .setHeader(Exchange.CONTENT_TYPE, constant("application/problem+json"))
                .process(exchange -> {
                    Exception ex = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
                    String correlationId = exchange.getProperty(CorrelationIdProcessor.MDC_CORRELATION_KEY, String.class);

                    ErrorResponse response = ErrorResponse.of(
                            "https://api.fluxaria.com/errors/internal-error",
                            "Internal Server Error",
                            500,
                            ex.getMessage() != null ? ex.getMessage() : "Error interno no controlado",
                            "/api/v1/orders",
                            correlationId
                    );
                    exchange.getIn().setBody(response);
                });

        setupRoutes();
    }

    /**
     * Metodo abstracto que deben implementar las subclases para registrar sus rutas Camel.
     */
    public abstract void setupRoutes() throws Exception;
}