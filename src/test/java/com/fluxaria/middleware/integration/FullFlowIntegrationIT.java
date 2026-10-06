package com.fluxaria.middleware.integration;

import com.fluxaria.middleware.domain.model.Order;
import com.fluxaria.middleware.domain.model.OrderItem;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@CamelSpringBootTest
public class FullFlowIntegrationIT {

    @Autowired
    private ProducerTemplate producerTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Debe procesar e insertar en base de datos y publicar en broker")
    void testEndToEndIntegrationFlow() {
        String correlationId = UUID.randomUUID().toString();
        OrderItem item = OrderItem.of("PROD-CI-1", 2, new BigDecimal("50.00"));
        Order order = Order.createNew(
                "ORD-CI-TEST-001",
                "Customer Test",
                "ES",
                "EUR",
                List.of(item)
        );

        Object response = producerTemplate.requestBodyAndHeader(
                "direct:orchestration.order.process",
                order,
                "X-Correlation-ID",
                correlationId
        );

        assertThat(response).isNotNull();

        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM orders WHERE order_id = ?",
                Integer.class,
                "ORD-CI-TEST-001"
        );
        assertThat(count).isEqualTo(1);

        Integer eventCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM order_events WHERE order_id = ? AND correlation_id = ?",
                Integer.class,
                "ORD-CI-TEST-001",
                correlationId
        );
        assertThat(eventCount).isGreaterThanOrEqualTo(1);
    }
}
