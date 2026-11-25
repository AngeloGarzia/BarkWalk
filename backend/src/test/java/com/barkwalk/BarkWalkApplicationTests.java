package com.barkwalk;

import com.barkwalk.support.PostgresTestcontainers;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class BarkWalkApplicationTests {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = PostgresTestcontainers.create();

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        PostgresTestcontainers.register(registry, POSTGRES);
    }

    @Test
    void contextLoads() {}
}
