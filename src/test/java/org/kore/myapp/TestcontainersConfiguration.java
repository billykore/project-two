package org.kore.myapp;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Real PostgreSQL and Kafka for integration tests (constitution Principle III), wired into Spring through
 * {@link ServiceConnection}. Spring's test-context cache shares one pair of containers across every test class that
 * imports this configuration.
 * <p>
 * Kafka is 3.9.1 rather than the README's 3.9.0: the 3.9.0 image rejects the {@code 0.0.0.0} advertised listener that
 * Testcontainers 2 configures while formatting storage, which the 3.9.1 patch release allows.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:16"));
    }

    @Bean
    @ServiceConnection
    KafkaContainer kafkaContainer() {
        return new KafkaContainer(DockerImageName.parse("apache/kafka:3.9.1"));
    }
}
