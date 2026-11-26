package com.barkwalk.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.barkwalk.booking.domain.Booking;
import com.barkwalk.booking.dto.BookingDtos.BookingResponse;
import com.barkwalk.booking.repository.BookingRepository;
import com.barkwalk.booking.service.BookingService;
import com.barkwalk.common.Role;
import com.barkwalk.payment.domain.Payment;
import com.barkwalk.payment.domain.WebhookEvent;
import com.barkwalk.payment.repository.PaymentRepository;
import com.barkwalk.payment.repository.WebhookEventRepository;
import com.barkwalk.support.SecurityTestSupport;
import com.barkwalk.walk.domain.Walk;
import com.barkwalk.walk.repository.WalkRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
    private BookingRepository bookingRepository;
    @Mock
    private WalkRepository walkRepository;
    @Mock
    private BookingService bookingService;
    @Mock
    private CommissionCalculator commissionCalculator;

    private PaymentService service;

    @BeforeEach
    void setUp() {
        service = new PaymentService(
                webhookEventRepository,
                paymentRepository,
                bookingRepository,
                walkRepository,
                bookingService,
                commissionCalculator,
                "");
    }

    @AfterEach
    void clear() {
        SecurityTestSupport.clear();
    }

    @Test
    void webhook_requiresSignatureWhenSecretConfigured() {
        PaymentService secured = new PaymentService(
                webhookEventRepository,
                paymentRepository,
                bookingRepository,
                walkRepository,
                bookingService,
                commissionCalculator,
                "whsec");
        assertThatThrownBy(() -> secured.handleWebhook(null, Map.of("id", "evt_1")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void webhook_duplicateEvent() {
        when(webhookEventRepository.existsByProviderAndEventId("stripe", "evt_1")).thenReturn(true);
        assertThat(service.handleWebhook("sig", Map.of("id", "evt_1")).status()).isEqualTo("duplicate");
        verify(webhookEventRepository, never()).save(any());
        verify(bookingService, never()).confirmPayment(any());
    }

    @Test
    void webhook_confirmsBookingAndRecordsIndepCommission() {
        when(webhookEventRepository.existsByProviderAndEventId("stripe", "evt_2")).thenReturn(false);
        when(webhookEventRepository.save(any(WebhookEvent.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bookingService.confirmPayment(42L))
                .thenReturn(new BookingResponse(42L, 1L, 2L, "CONFIRMED", 2000, true, Instant.now()));

        Booking booking = new Booking();
        booking.setUserId(9L);
        booking.setWalkId(1L);
        booking.setPriceCents(2000);
        when(bookingRepository.findById(42L)).thenReturn(Optional.of(booking));
        when(paymentRepository.findByBookingId(42L)).thenReturn(Optional.empty());

        Walk walk = new Walk();
        walk.setWalkType("INDEP_GUIDED");
        when(walkRepository.findById(1L)).thenReturn(Optional.of(walk));
        when(commissionCalculator.commissionCents(2000)).thenReturn(300);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> payload = Map.of(
                "id", "evt_2", "data", Map.of("object", Map.of("booking_id", "42", "id", "pi_1")));
        assertThat(service.handleWebhook("sig", payload).status()).isEqualTo("ok");
        verify(bookingService).confirmPayment(42L);

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertThat(captor.getValue().getCommissionCents()).isEqualTo(300);
        assertThat(captor.getValue().getAmountCents()).isEqualTo(2000);
    }

    @Test
    void mine_listsSanitizedPayments() {
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
