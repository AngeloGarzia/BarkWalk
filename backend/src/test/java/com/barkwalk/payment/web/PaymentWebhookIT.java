package com.barkwalk.payment.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.barkwalk.support.PostgresTestcontainers;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

// Bout en bout HTTP : le corps brut doit arriver intact jusqu'à la vérification de signature Stripe.
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class PaymentWebhookIT {

    private static final String SECRET = "whsec_it_secret";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = PostgresTestcontainers.create();

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        PostgresTestcontainers.register(registry, POSTGRES);
        registry.add("barkwalk.stripe.webhook-secret", () -> SECRET);
    }

    @Autowired
    private MockMvc mockMvc;

    // Espaces et ordre de clés volontairement « non canoniques » : une re-sérialisation casserait la signature.
    private static final String PAYLOAD =
            "{ \"type\": \"customer.created\",  \"id\": \"evt_it_1\", \"object\": \"event\", \"data\": {\"object\": {}} }";

    @Test
    void signedEvent_isAcceptedWithoutAuthentication() throws Exception {
        mockMvc.perform(post("/api/payments/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", sign(PAYLOAD, SECRET))
                        .content(PAYLOAD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void forgedEvent_isRejected() throws Exception {
        mockMvc.perform(post("/api/payments/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", sign(PAYLOAD, "whsec_attacker"))
                        .content(PAYLOAD))
                .andExpect(status().isBadRequest());
    }

    private static String sign(String payload, String secret) throws Exception {
        long timestamp = Instant.now().getEpochSecond();
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8));
        return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(digest);
    }
}
