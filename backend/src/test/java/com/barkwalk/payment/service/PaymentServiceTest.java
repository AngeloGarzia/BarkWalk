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
import java.util.Map;
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

    @Test
    void webhook_requiresSignatureWhenSecretConfigured() {
        PaymentService service = new PaymentService(webhookEventRepository, paymentRepository, bookingService, "whsec");
        assertThatThrownBy(() -> service.handleWebhook(null, Map.of("id", "evt_1")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void webhook_duplicateEvent() {
        PaymentService service = new PaymentService(webhookEventRepository, paymentRepository, bookingService, "");
        when(webhookEventRepository.existsByProviderAndEventId("stripe", "evt_1")).thenReturn(true);
        assertThat(service.handleWebhook("sig", Map.of("id", "evt_1")).status()).isEqualTo("duplicate");
        verify(webhookEventRepository, never()).save(any());
    }

    @Test
    void webhook_confirmsBooking() {
        PaymentService service = new PaymentService(webhookEventRepository, paymentRepository, bookingService, "");
        when(webhookEventRepository.existsByProviderAndEventId("stripe", "evt_2")).thenReturn(false);
        when(webhookEventRepository.save(any(WebhookEvent.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bookingService.confirmPayment(42L))
                .thenReturn(new BookingResponse(42L, 1L, 2L, "CONFIRMED", 0, false, Instant.now()));

        Map<String, Object> payload = Map.of(
                "id", "evt_2", "data", Map.of("object", Map.of("booking_id", "42")));
        assertThat(service.handleWebhook("sig", payload).status()).isEqualTo("ok");
        verify(bookingService).confirmPayment(42L);
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
