package com.barkwalk.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.barkwalk.booking.dto.BookingDtos.BookingResponse;
import com.barkwalk.booking.service.BookingService;
import com.barkwalk.common.Role;
import com.barkwalk.payment.domain.Payment;
import com.barkwalk.payment.domain.WebhookEvent;
import com.barkwalk.payment.repository.PaymentRepository;
import com.barkwalk.payment.repository.WebhookEventRepository;
import com.barkwalk.support.SecurityTestSupport;
import java.time.Instant;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private WebhookEventRepository webhookEventRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private BookingService bookingService;

    @AfterEach
    void clear() {
        SecurityTestSupport.clear();
    }

    private static final String SECRET = "whsec_test_secret";

    @Test
    void webhook_rejectedWhenSecretNotConfigured() {
        PaymentService service = new PaymentService(webhookEventRepository, paymentRepository, bookingService, "");
        String payload = checkoutCompleted("evt_1", "42");

        assertThatThrownBy(() -> service.handleWebhook(sign(payload, SECRET), payload))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        verify(bookingService, never()).confirmPayment(any());
    }

    @Test
    void webhook_rejectsMissingSignature() {
        PaymentService service = new PaymentService(webhookEventRepository, paymentRepository, bookingService, SECRET);
        String payload = checkoutCompleted("evt_1", "42");

        assertThatThrownBy(() -> service.handleWebhook(null, payload))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verify(bookingService, never()).confirmPayment(any());
    }

    @Test
    void webhook_rejectsForgedSignature() {
        PaymentService service = new PaymentService(webhookEventRepository, paymentRepository, bookingService, SECRET);
        String payload = checkoutCompleted("evt_1", "42");

        assertThatThrownBy(() -> service.handleWebhook(sign(payload, "whsec_attacker"), payload))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verify(bookingService, never()).confirmPayment(any());
        verify(webhookEventRepository, never()).save(any());
    }

    @Test
    void webhook_rejectsPayloadTamperedAfterSigning() {
        PaymentService service = new PaymentService(webhookEventRepository, paymentRepository, bookingService, SECRET);
        String signature = sign(checkoutCompleted("evt_1", "42"), SECRET);

        assertThatThrownBy(() -> service.handleWebhook(signature, checkoutCompleted("evt_1", "43")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verify(bookingService, never()).confirmPayment(any());
    }

    @Test
    void webhook_duplicateEvent() {
        PaymentService service = new PaymentService(webhookEventRepository, paymentRepository, bookingService, SECRET);
        when(webhookEventRepository.existsByProviderAndEventId("stripe", "evt_1")).thenReturn(true);
        String payload = checkoutCompleted("evt_1", "42");

        assertThat(service.handleWebhook(sign(payload, SECRET), payload).status()).isEqualTo("duplicate");
        verify(webhookEventRepository, never()).save(any());
        verify(bookingService, never()).confirmPayment(any());
    }

    @Test
    void webhook_confirmsBookingFromSignedCheckoutCompleted() {
        PaymentService service = new PaymentService(webhookEventRepository, paymentRepository, bookingService, SECRET);
        when(webhookEventRepository.existsByProviderAndEventId("stripe", "evt_2")).thenReturn(false);
        when(webhookEventRepository.save(any(WebhookEvent.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bookingService.confirmPayment(42L))
                .thenReturn(new BookingResponse(42L, 1L, 2L, "CONFIRMED", 0, false, Instant.now()));
        String payload = checkoutCompleted("evt_2", "42");

        assertThat(service.handleWebhook(sign(payload, SECRET), payload).status()).isEqualTo("ok");
        verify(bookingService).confirmPayment(42L);
    }

    @Test
    void webhook_ignoresEventTypesThatAreNotPaymentSuccess() {
        PaymentService service = new PaymentService(webhookEventRepository, paymentRepository, bookingService, SECRET);
        when(webhookEventRepository.existsByProviderAndEventId("stripe", "evt_3")).thenReturn(false);
        when(webhookEventRepository.save(any(WebhookEvent.class))).thenAnswer(inv -> inv.getArgument(0));
        String payload = event("evt_3", "checkout.session.expired", "42");

        assertThat(service.handleWebhook(sign(payload, SECRET), payload).status()).isEqualTo("ok");
        verify(bookingService, never()).confirmPayment(any());
    }

    private static String checkoutCompleted(String eventId, String bookingId) {
        return event(eventId, "checkout.session.completed", bookingId);
    }

    private static String event(String eventId, String type, String bookingId) {
        return """
                {"id":"%s","object":"event","type":"%s","data":{"object":{"id":"cs_test","object":"checkout.session","metadata":{"booking_id":"%s"}}}}"""
                .formatted(eventId, type, bookingId);
    }

    // Reproduit l'en-tete Stripe-Signature : t=<ts>,v1=HMAC_SHA256(secret, "<ts>.<payload>").
    private static String sign(String payload, String secret) {
        try {
            long timestamp = Instant.now().getEpochSecond();
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8));
            return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void mine_listsSanitizedPayments() {
        PaymentService service = new PaymentService(webhookEventRepository, paymentRepository, bookingService, "");
        SecurityTestSupport.authenticate(9L, "u@example.com", Role.USER);
        Payment payment = new Payment();
        payment.setUserId(9L);
        payment.setAmountCents(1000);
        payment.setPaymentType("BOOKING");
        payment.setStatus("SUCCEEDED");
        payment.setStripePaymentIntent("pi_secret");
        payment.setCommissionCents(100);
        when(paymentRepository.findByUserIdOrderByCreatedAtDesc(9L)).thenReturn(List.of(payment));
        var responses = service.mine();
        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().amountCents()).isEqualTo(1000);
    }
}
