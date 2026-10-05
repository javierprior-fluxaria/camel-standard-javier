package com.fluxaria.middleware.integration;

import org.apache.camel.ProducerTemplate;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
@CamelSpringBootTest
public class FullFlowIntegrationIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("orders_db")
            .withUsername("fluxaria")
            .withPassword("fluxaria_password")
            .withInitScript("schema.sql");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("middleware.kafka.brokers", kafka::getBootstrapServers);
    }

    @Autowired
    private ProducerTemplate producerTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Debe procesar e insertar en base de datos y publicar en Kafka con contenedores efimeros")
    void testEndToEndContainerizedFlow() {
        String correlationId = UUID.randomUUID().toString();
        String jsonPayload = """
            {
                "orderId": "ORD-CI-TEST-001",
                "customer": "Testcontainers Customer",
                "countryCode": "ES",
                "items": [
                    {
                        "productId": "PROD-CI-1",
                        "quantity": 2,
                        "unitPrice": 50.0,
                        "currency": "EUR"
                    }
                ]
            }
            """;

        Object response = producerTemplate.requestBodyAndHeader(
                "direct:orchestration.order.process",
                jsonPayload,
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
