package com.barkwalk;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
class BarkWalkApplicationTests {

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.datasource.url",
                () -> "jdbc:h2:mem:barkwalk;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=KEY,VALUE");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.docker.compose.enabled", () -> "false");
        registry.add("barkwalk.demo.seed-users", () -> "false");
        registry.add("barkwalk.jwt.secret", () -> "barkwalk-test-secret-key-32chars-min!!");
    }

    @Test
    void contextLoads() {
    }
}
