package com.fluxaria.middleware.shared.health;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * HealthIndicator para Apache Kafka expuesto en /actuator/health.
 * Verifica la disponibilidad del cluster de Kafka mediante AdminClient.
 */
@Component
public class KafkaHealthIndicator implements HealthIndicator {

    @Value("${integration.kafka.brokers:localhost:9092}")
    private String kafkaBrokers;

    @Override
    public Health health() {
        try (AdminClient adminClient = AdminClient.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBrokers,
                AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, "2000",
                AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, "2000"
        ))) {
            DescribeClusterResult result = adminClient.describeCluster();
            String clusterId = result.clusterId().get(2, TimeUnit.SECONDS);
            int nodeCount = result.nodes().get(2, TimeUnit.SECONDS).size();
            return Health.up()
                    .withDetail("clusterId", clusterId)
                    .withDetail("nodes", nodeCount)
                    .withDetail("brokers", kafkaBrokers)
                    .build();
        } catch (Exception e) {
            return Health.down()
                    .withDetail("brokers", kafkaBrokers)
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
