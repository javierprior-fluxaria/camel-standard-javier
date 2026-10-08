package com.fluxaria.middleware.shared.error;

import com.fluxaria.middleware.shared.logging.CorrelationIdProcessor;
import com.fluxaria.middleware.shared.model.ErrorResponse;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.springframework.dao.DuplicateKeyException;

import java.sql.SQLException;

/**
 * BaseRouteBuilder transversal para estandarizar el manejo de errores segun RFC 7807,
 * reintentos, control de circuitos y logs limpios (sin volcados masivos de stacktraces para errores de negocio).
 */
public abstract class BaseRouteBuilder extends RouteBuilder {

    @Override
    public void configure() throws Exception {

        // 0. Hook prioritario para subclases (politicas de reintentos antes de capturadores globales)
        setupCustomErrorHandlers();

        // 1. Errores funcionales de validacion de negocio -> HTTP 400 Bad Request
        onException(BusinessValidationException.class)
                .handled(true)
                .logHandled(true)
                .logStackTrace(false)
                .log(LoggingLevel.WARN, "Error de validacion de negocio: ${exception.message}")
                .process(buildValidationProblemDetails());

        // 2. Errores de duplicidad en Base de Datos (Idempotencia SQL) -> HTTP 409 Conflict
        onException(DuplicateKeyException.class, SQLException.class)
                .onWhen(exchange -> {
                    Exception ex = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
                    if (ex == null) {
                        ex = exchange.getException();
                    }
                    return ex != null && ex.getMessage() != null && (ex.getMessage().contains("duplicate key") || ex.getMessage().contains("unique constraint"));
                })
                .handled(true)
                .logHandled(true)
                .logStackTrace(false)
                .log(LoggingLevel.WARN, "Idempotencia activada: Pedido duplicado en base de datos: ${exception.message}")
                .process(buildProblemDetails(409, "https://api.fluxaria.com/errors/conflict", "Duplicate Resource Conflict", "El pedido ya existe en la base de datos y no se volvera a procesar."));

        // 3. Errores HTTP de backends (409 Conflict, 4xx, etc.)
        onException(HttpOperationFailedException.class)
                .onWhen(exchange -> {
                    HttpOperationFailedException ex = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, HttpOperationFailedException.class);
                    if (ex == null) {
                        ex = exchange.getException(HttpOperationFailedException.class);
                    }
                    return ex != null && ex.getStatusCode() == 409;
                })
                .handled(true)
                .logHandled(true)
                .logStackTrace(false)
                .log(LoggingLevel.WARN, "Conflicto de idempotencia / duplicado detectado en backend: ${exception.message}")
                .process(buildProblemDetails(409, "https://api.fluxaria.com/errors/conflict", "Duplicate Resource Conflict", "El pedido ya existe en el sistema destino o ha sido procesado previamente."));

        // 4. Excepciones genericas no controladas -> HTTP 500 / 502
        onException(Exception.class)
                .handled(true)
                .logHandled(true)
                .log(LoggingLevel.ERROR, "Error en flujo middleware: ${exception.message}")
                .process(buildProblemDetails(500, "https://api.fluxaria.com/errors/internal-error", "Internal Server Error", "Error interno no controlado"));

        setupRoutes();
    }

    /**
     * Hook opcional para que clases hijas configuren clausulas onException prioritarias
     * (por ejemplo politicas de reintentos) antes de las globales y del catch-all.
     */
    protected void setupCustomErrorHandlers() throws Exception {
        // Implementacion por defecto vacia
    }

    /**
     * Helper transversal para construir respuestas RFC 7807 (Problem Details).
     */
    protected org.apache.camel.Processor buildProblemDetails(int status, String typeUrl, String title, String defaultDetail) {
        return exchange -> {
            Exception ex = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
            if (ex == null) {
                ex = exchange.getException();
            }
            String correlationId = exchange.getProperty(CorrelationIdProcessor.MDC_CORRELATION_KEY, String.class);
            String path = exchange.getMessage().getHeader(Exchange.HTTP_URI, String.class);
            String detail = (ex != null && ex.getMessage() != null) ? ex.getMessage() : defaultDetail;

            ErrorResponse response = ErrorResponse.of(
                    typeUrl,
                    title,
                    status,
                    detail,
                    path != null ? path : "/api/v1/orders",
                    correlationId
            );
            exchange.getMessage().setBody(response);
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, status);
            exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, "application/problem+json");
        };
    }

    /**
     * Helper para errores funcionales de validacion de negocio.
     */
    protected org.apache.camel.Processor buildValidationProblemDetails() {
        return exchange -> {
            BusinessValidationException ex = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, BusinessValidationException.class);
            if (ex == null && exchange.getException() instanceof BusinessValidationException) {
                ex = (BusinessValidationException) exchange.getException();
            }
            String correlationId = exchange.getProperty(CorrelationIdProcessor.MDC_CORRELATION_KEY, String.class);
            String path = exchange.getMessage().getHeader(Exchange.HTTP_URI, String.class);

            String message = ex != null ? ex.getMessage() : "Error de validacion funcional";
            java.util.List<String> errors = ex != null ? ex.getValidationErrors() : java.util.Collections.emptyList();

            ErrorResponse response = ErrorResponse.ofValidation(
                    message,
                    path != null ? path : "/api/v1/orders",
                    correlationId,
                    errors
            );
            exchange.getMessage().setBody(response);
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 400);
            exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, "application/problem+json");
        };
    }

    public abstract void setupRoutes() throws Exception;
}