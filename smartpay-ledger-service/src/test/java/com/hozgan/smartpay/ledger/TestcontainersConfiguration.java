package com.hozgan.smartpay.ledger;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared Testcontainers configuration for all integration tests.
 *
 * <p>A single PostgreSQL 16 container is started once per test JVM and reused across all
 * {@code @Import(TestcontainersConfiguration.class)} test classes.
 * Spring Boot's {@code @ServiceConnection} auto-wires datasource URL/credentials so no
 * manual {@code spring.datasource.*} properties are needed in test config.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    public PostgreSQLContainer<?> postgreSQLContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                .withDatabaseName("smartpay_ledger_test")
                .withUsername("test")
                .withPassword("test")
                .withReuse(true);
    }
}
