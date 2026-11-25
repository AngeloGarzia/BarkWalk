package com.barkwalk.payment.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.barkwalk.booking.service.BookingService;
import com.barkwalk.booking.service.BookingService.BookingResponse;
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
class PaymentControllerTest {

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
        PaymentController controller =
                new PaymentController(webhookEventRepository, paymentRepository, bookingService, "whsec");
        assertThatThrownBy(() -> controller.webhook(null, Map.of("id", "evt_1")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void webhook_duplicateEvent() {
        PaymentController controller =
                new PaymentController(webhookEventRepository, paymentRepository, bookingService, "");
        when(webhookEventRepository.existsByProviderAndEventId("stripe", "evt_1")).thenReturn(true);
        assertThat(controller.webhook("sig", Map.of("id", "evt_1"))).containsEntry("status", "duplicate");
        verify(webhookEventRepository, never()).save(any());
    }

    @Test
    void webhook_confirmsBooking() {
        PaymentController controller =
                new PaymentController(webhookEventRepository, paymentRepository, bookingService, "");
        when(webhookEventRepository.existsByProviderAndEventId("stripe", "evt_2")).thenReturn(false);
        when(webhookEventRepository.save(any(WebhookEvent.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bookingService.confirmPayment(42L))
                .thenReturn(new BookingResponse(42L, 1L, 2L, "CONFIRMED", 0, false, Instant.now()));

        Map<String, Object> payload = Map.of(
                "id",
                "evt_2",
                "data",
                Map.of("object", Map.of("booking_id", "42")));
        assertThat(controller.webhook("sig", payload)).containsEntry("status", "ok");
        verify(bookingService).confirmPayment(42L);
    }

    @Test
    void mine_listsPayments() {
        PaymentController controller =
                new PaymentController(webhookEventRepository, paymentRepository, bookingService, "");
        SecurityTestSupport.authenticate(9L, "u@example.com", Role.USER);
        when(paymentRepository.findByUserIdOrderByCreatedAtDesc(9L)).thenReturn(List.of(new Payment()));
        assertThat(controller.mine()).hasSize(1);
    }
}
