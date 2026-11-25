package com.barkwalk.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

// Propriétés Spring pour un conteneur PostgreSQL (Flyway from scratch).
public final class PostgresTestcontainers {
    public static final DockerImageName IMAGE = DockerImageName.parse("postgres:17-alpine");

    private PostgresTestcontainers() {}

    public static PostgreSQLContainer<?> create() {
        return new PostgreSQLContainer<>(IMAGE)
                .withDatabaseName("barkwalk")
                .withUsername("barkwalk")
                .withPassword("barkwalk");
    }

    public static void register(DynamicPropertyRegistry registry, PostgreSQLContainer<?> postgres) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.docker.compose.enabled", () -> "false");
        registry.add("barkwalk.demo.seed-users", () -> "false");
        registry.add("barkwalk.jwt.secret", () -> "barkwalk-test-secret-key-32chars-min!!");
        registry.add("barkwalk.cors.allowed-origins", () -> "http://localhost:4200");
        registry.add("barkwalk.stripe.webhook-secret", () -> "");
    }
}
