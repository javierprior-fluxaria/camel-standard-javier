package com.fluxaria.middleware.inbound.rest;

import com.fluxaria.middleware.inbound.rest.dto.OrderRequestDto;
import com.fluxaria.middleware.inbound.rest.mapper.OrderInboundMapper;
import com.fluxaria.middleware.orchestration.OrderProcessRoute;
import com.fluxaria.middleware.shared.error.BaseRouteBuilder;
import com.fluxaria.middleware.shared.logging.CorrelationIdProcessor;
import com.fluxaria.middleware.shared.model.ErrorResponse;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.model.rest.RestBindingMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Adaptador Inbound REST (Experience API).
 * Expone la API publica HTTP POST /api/v1/orders y se encarga de:
 * 1. Correlacion y trazabilidad (MDC).
 * 2. Mapeo de entrada OrderRequestDto -> Order Canónico.
 * 3. Invocacion síncrona al Orquestador.
 * 4. Mapeo de salida Order -> OrderResponseDto (HTTP 201 Created).
 */
@Component
public class OrderRestRoute extends BaseRouteBuilder {

    @Autowired
    private CorrelationIdProcessor correlationIdProcessor;

    @Autowired
    private OrderInboundMapper inboundMapper;

    @Override
    public void setupRoutes() {
        restConfiguration()
                .component("servlet")
                .bindingMode(RestBindingMode.json)
                .dataFormatProperty("json.in.disableFeatures", "FAIL_ON_UNKNOWN_PROPERTIES");

        rest("/v1/orders")
                .post()
                .type(OrderRequestDto.class)
                .consumes("application/json")
                .produces("application/json")
                .to("direct:inbound.rest.process-order");

        from("direct:inbound.rest.process-order")
                .routeId("inbound.rest.process-order")
                // 1. Inyectar o propagar X-Correlation-ID en Exchange y MDC
                .process(correlationIdProcessor)
                .log(LoggingLevel.INFO, "Recibida peticion POST /api/v1/orders para cliente: ${body.customerId}")

                // 2. Mapear DTO de entrada al Modelo Canonico de Dominio
                .bean(inboundMapper, "toDomain")

                // 3. Delegar en el Orquestador
                .to(OrderProcessRoute.DIRECT_PROCESS)

                // 4. Mapear Modelo Canonico procesado al DTO de salida solo en caso de exito
                .choice()
                    .when(body().isInstanceOf(ErrorResponse.class))
                        // Si ya es una respuesta de error generada por onException, se devuelve directamente
                    .otherwise()
                        .bean(inboundMapper, "toResponseDto")
                        .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(201))
                        .log(LoggingLevel.INFO, "Respondiendo 201 Created para pedido: ${body.orderId}")
                .end();
    }
}