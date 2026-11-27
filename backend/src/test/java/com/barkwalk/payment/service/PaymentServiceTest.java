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
import com.barkwalk.membership.service.MembershipService;
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
    private MembershipService membershipService;
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
                membershipService,
                commissionCalculator,
                "");
    }

    @AfterEach
    void clear() {
        SecurityTestSupport.clear();
    }

    // Exige la signature du webhook quand secret configuré.
    @Test
    void webhook_requiresSignatureWhenSecretConfigured() {
        PaymentService secured = new PaymentService(
                webhookEventRepository,
                paymentRepository,
                bookingRepository,
                walkRepository,
                bookingService,
                membershipService,
                commissionCalculator,
                "whsec");
        assertThatThrownBy(() -> secured.handleWebhook(null, Map.of("id", "evt_1")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // Ignore les événements dupliqués.
    @Test
    void webhook_duplicateEvent() {
        when(webhookEventRepository.existsByProviderAndEventId("stripe", "evt_1")).thenReturn(true);
        assertThat(service.handleWebhook("sig", Map.of("id", "evt_1")).status()).isEqualTo("duplicate");
        verify(webhookEventRepository, never()).save(any());
        verify(bookingService, never()).confirmPayment(any());
    }

    // Confirme la réservation et enregistre la commission indépendante.
    @Test
    void webhook_confirmsBookingAndRecordsIndepCommission() {
        when(webhookEventRepository.existsByProviderAndEventId("stripe", "evt_2")).thenReturn(false);
        when(webhookEventRepository.save(any(WebhookEvent.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bookingService.confirmPayment(42L))
                .thenReturn(new BookingResponse(
                        42L, 1L, 2L, "CONFIRMED", 2000, true, Instant.now(), null, false, false,
                        null, null, null, null, null, null, null));

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

    // Liste les paiements de l'utilisateur sans données sensibles.
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

    // Active l'adhésion et enregistre le paiement via webhook.
    @Test
    void webhook_activatesMembershipAndRecordsPayment() {
        when(webhookEventRepository.existsByProviderAndEventId("stripe", "evt_m")).thenReturn(false);
        when(webhookEventRepository.save(any(WebhookEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        com.barkwalk.membership.domain.Membership membership = new com.barkwalk.membership.domain.Membership();
        setId(membership, 5L);
        membership.setUserId(9L);
        when(membershipService.activateOnPayment(5L)).thenReturn(membership);
        when(paymentRepository.findByMembershipId(5L)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> payload = Map.of(
                "id",
                "evt_m",
                "data",
                Map.of("object", Map.of("membership_id", "5", "id", "pi_m", "amount_cents", "4900")));
        assertThat(service.handleWebhook("sig", payload).status()).isEqualTo("ok");

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertThat(captor.getValue().getPaymentType()).isEqualTo("MEMBERSHIP");
        assertThat(captor.getValue().getAmountCents()).isEqualTo(4900);
        assertThat(captor.getValue().getMembershipId()).isEqualTo(5L);
    }

    // Ignore un paiement de réservation déjà enregistré.
    @Test
    void webhook_skipsExistingBookingPayment() {
        when(webhookEventRepository.existsByProviderAndEventId("stripe", "evt_3")).thenReturn(false);
        when(webhookEventRepository.save(any(WebhookEvent.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bookingService.confirmPayment(42L))
                .thenReturn(new BookingResponse(
                        42L, 1L, 2L, "CONFIRMED", 2000, true, Instant.now(), null, false, false,
                        null, null, null, null, null, null, null));
        Booking booking = new Booking();
        booking.setUserId(9L);
        booking.setWalkId(1L);
        booking.setPriceCents(2000);
        when(bookingRepository.findById(42L)).thenReturn(Optional.of(booking));
        when(paymentRepository.findByBookingId(42L)).thenReturn(Optional.of(new Payment()));

        Map<String, Object> payload =
                Map.of("id", "evt_3", "data", Map.of("object", Map.of("booking_id", "42", "id", "pi_1")));
        assertThat(service.handleWebhook("sig", payload).status()).isEqualTo("ok");
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    // Liste tous les paiements pour l'admin.
    @Test
    void allForAdmin_mapsPayments() {
        Payment payment = new Payment();
        payment.setAmountCents(500);
        payment.setPaymentType("BOOKING");
        payment.setStatus("SUCCEEDED");
        when(paymentRepository.findAll()).thenReturn(List.of(payment));
        assertThat(service.allForAdmin()).hasSize(1);
        assertThat(service.allForAdmin().getFirst().amountCents()).isEqualTo(500);
    }

    private static void setId(Object entity, long id) {
        try {
            var f = entity.getClass().getDeclaredField("id");
            f.setAccessible(true);
            f.set(entity, id);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
