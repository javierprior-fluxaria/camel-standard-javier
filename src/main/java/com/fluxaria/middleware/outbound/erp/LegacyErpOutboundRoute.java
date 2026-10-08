package com.fluxaria.middleware.outbound.erp;

import com.fluxaria.middleware.domain.model.Order;
import com.fluxaria.middleware.outbound.erp.mapper.LegacyErpMapper;
import com.fluxaria.middleware.shared.error.BaseRouteBuilder;
import com.fluxaria.middleware.shared.model.ErrorResponse;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.ConnectException;
import java.net.SocketTimeoutException;

/**
 * Adaptador Outbound hacia el Legacy ERP (POST http://legacy-erp:8080/erp/orders).
 */
@Component
public class LegacyErpOutboundRoute extends BaseRouteBuilder {

    public static final String ROUTE_ID = "outbound.erp.submit";
    public static final String DIRECT_SUBMIT = "direct:" + ROUTE_ID;

    @Value("${integration.outbound.erp.base-url:http://localhost:8080/api/mock-erp/erp/orders}")
    private String erpBaseUrl;

    @Value("${integration.outbound.erp.connect-timeout-ms:2000}")
    private int connectTimeout;

    @Value("${integration.outbound.erp.read-timeout-ms:3000}")
    private int readTimeout;

    @Autowired
    private LegacyErpMapper legacyErpMapper;

    @Override
    protected void setupCustomErrorHandlers() {
        // Politica de reintentos para errores transitorios de red o 500 intermitente
        onException(HttpOperationFailedException.class)
                .onWhen(exchange -> {
                    HttpOperationFailedException ex = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, HttpOperationFailedException.class);
                    if (ex == null) {
                        ex = exchange.getException(HttpOperationFailedException.class);
                    }
                    return ex != null && (ex.getStatusCode() == 500 || ex.getStatusCode() == 502);
                })
                .maximumRedeliveries(2)
                .redeliveryDelay(1000)
                .backOffMultiplier(2.0)
                .useExponentialBackOff()
                .retryAttemptedLogLevel(LoggingLevel.WARN)
                .logRetryStackTrace(false)
                .log(LoggingLevel.WARN, "Reintentando llamada a Legacy ERP tras fallo HTTP 500...");

        // Politica de reintentos para timeouts de conexion / lectura
        onException(ConnectException.class, SocketTimeoutException.class)
                .maximumRedeliveries(1)
                .redeliveryDelay(500)
                .logRetryStackTrace(false)
                .log(LoggingLevel.WARN, "Reintentando llamada a Legacy ERP tras timeout de red...");
    }

    @Override
    public void setupRoutes() {
        from(DIRECT_SUBMIT)
                .routeId(ROUTE_ID)
                .log(LoggingLevel.INFO, "Preparando envio al Legacy ERP para pedido ${body.orderId}...")
                .setProperty("originalOrder", body())

                // 1. Mapeo a DTO propio del ERP
                .bean(legacyErpMapper, "toLegacyDto")
                .marshal().json()

                // 2. Proteccion con Circuit Breaker Resilience4j
                .circuitBreaker()
                    .inheritErrorHandler(true)
                    .resilience4jConfiguration()
                        .failureRateThreshold(50)
                        .waitDurationInOpenState(5)
                        .slidingWindowSize(5)
                    .end()
                    .setHeader(Exchange.HTTP_METHOD, constant("POST"))
                    .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
                    .setHeader("X-Correlation-ID", simple("${exchangeProperty.correlationId}"))
                    .toD(erpBaseUrl + "?bridgeEndpoint=true&throwExceptionOnFailure=true&httpClient.connectTimeout=" + connectTimeout + "&httpClient.responseTimeout=" + readTimeout)
                .end()

                // 3. Al completarse con exito, actualizar el estado del dominio
                .choice()
                    .when(body().isInstanceOf(ErrorResponse.class))
                        .log(LoggingLevel.WARN, "Llamada a Legacy ERP fallida (ErrorResponse detectado para pedido ${exchangeProperty.originalOrder.orderId})")
                    .otherwise()
                        .setBody(exchangeProperty("originalOrder"))
                        .bean(legacyErpMapper, "handleSuccess")
                        .log(LoggingLevel.INFO, "Respuesta exitosa de Legacy ERP para pedido ${body.orderId}")
                .end();
    }
}