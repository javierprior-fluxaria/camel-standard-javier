package com.fluxaria.middleware.backend.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fluxaria.middleware.shared.error.BaseRouteBuilder;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simulador del Legacy ERP (POST /api/mock-erp/erp/orders).
 * Reproduce exactamente los 5 escenarios de prueba segun el customerId:
 * 1. CUST-OK: Responde 200 OK inmediatamente.
 * 2. CUST-SLOW: Tarda 5s (provoca timeout del cliente HTTP configurado a 3s).
 * 3. CUST-ERR500: Falla con 500 dos veces seguidas y a la tercera llamada responde 200 OK (prueba reintentos).
 * 4. CUST-ERR503: Responde 503 Service Unavailable de forma permanente (prueba apertura de Circuit Breaker).
 * 5. CUST-DUP: Responde 409 Conflict simulando duplicidad en el ERP (prueba idempotencia).
 */
@Component
public class MockErpBackendRoute extends BaseRouteBuilder {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicInteger retryCount = new AtomicInteger(0);
    private final Map<String, Boolean> registeredOrders = new ConcurrentHashMap<>();

    @Override
    public void setupRoutes() {
        from("servlet:/mock-erp/erp/orders?matchOnUriPrefix=true")
                .routeId("backend.mock.erp")
                .log(LoggingLevel.INFO, "Mock ERP recibido payload: ${body}")
                .process(exchange -> {
                    String body = exchange.getIn().getBody(String.class);
                    var jsonNode = objectMapper.readTree(body);

                    String customerId = jsonNode.path("customerId").asText("UNKNOWN");
                    String orderId = jsonNode.path("legacyOrderId").asText("UNKNOWN");

                    // 1. Escenario CUST-SLOW -> Delay de 5 segundos
                    if ("CUST-SLOW".equalsIgnoreCase(customerId)) {
                        log.info("[MOCK ERP] Escenario CUST-SLOW: durmiendo 5 segundos...");
                        Thread.sleep(5000);
                        exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 200);
                        exchange.getMessage().setBody("{\"status\":\"OK\",\"message\":\"Processed after 5s\"}");
                        return;
                    }

                    // 2. Escenario CUST-ERR500 -> 500 intermitente (falla 2 veces, a la 3a OK)
                    if ("CUST-ERR500".equalsIgnoreCase(customerId)) {
                        int attempt = retryCount.incrementAndGet();
                        if (attempt < 3) {
                            log.warn("[MOCK ERP] Escenario CUST-ERR500: simulando fallo interno 500 (intento #{})", attempt);
                            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 500);
                            exchange.getMessage().setBody("{\"error\":\"Internal ERP Database Deadlock (transient)\"}");
                            return;
                        } else {
                            log.info("[MOCK ERP] Escenario CUST-ERR500: intento exitoso #3 tras reintentos (intento #{})", attempt);
                            retryCount.set(0);
                            registeredOrders.put(orderId, true);
                            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 200);
                            exchange.getMessage().setBody("{\"status\":\"OK\",\"erpReference\":\"ERP-RETRY-" + orderId + "\"}");
                            return;
                        }
                    }

                    // 3. Escenario CUST-ERR503 -> 503 permanente (Circuit Breaker)
                    if ("CUST-ERR503".equalsIgnoreCase(customerId)) {
                        log.error("[MOCK ERP] Escenario CUST-ERR503: simulando servicio no disponible 503");
                        exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 503);
                        exchange.getMessage().setBody("{\"error\":\"Legacy ERP in Maintenance Mode\"}");
                        return;
                    }

                    // 4. Escenario CUST-DUP o pedido ya registrado -> 409 Conflict
                    if ("CUST-DUP".equalsIgnoreCase(customerId) || registeredOrders.containsKey(orderId)) {
                        log.warn("[MOCK ERP] Escenario 409 Conflict: pedido duplicado {}", orderId);
                        exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 409);
                        exchange.getMessage().setBody("{\"error\":\"Order already exists in Legacy ERP: " + orderId + "\"}");
                        return;
                    }

                    // 5. Escenario OK (Default / CUST-OK)
                    registeredOrders.put(orderId, true);
                    log.info("[MOCK ERP] Pedido {} registrado exitosamente (200 OK)", orderId);
                    exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 200);
                    exchange.getMessage().setBody("{\"status\":\"CONFIRMED\",\"erpReference\":\"ERP-REG-" + orderId + "\"}");
                });
    }

    public void resetState() {
        retryCount.set(0);
        registeredOrders.clear();
    }
}