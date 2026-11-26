package com.barkwalk.payment.service;

import com.barkwalk.booking.domain.Booking;
import com.barkwalk.booking.repository.BookingRepository;
import com.barkwalk.booking.service.BookingService;
import com.barkwalk.common.SecurityUtils;
import com.barkwalk.membership.domain.Membership;
import com.barkwalk.membership.service.MembershipService;
import com.barkwalk.payment.domain.Payment;
import com.barkwalk.payment.domain.WebhookEvent;
import com.barkwalk.payment.dto.PaymentDtos.PaymentResponse;
import com.barkwalk.payment.dto.PaymentDtos.WebhookAck;
import com.barkwalk.payment.repository.PaymentRepository;
import com.barkwalk.payment.repository.WebhookEventRepository;
import com.barkwalk.walk.domain.Walk;
import com.barkwalk.walk.repository.WalkRepository;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PaymentService {
    private final WebhookEventRepository webhookEventRepository;
    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final WalkRepository walkRepository;
    private final BookingService bookingService;
    private final MembershipService membershipService;
    private final CommissionCalculator commissionCalculator;
    private final String webhookSecret;

    public PaymentService(
            WebhookEventRepository webhookEventRepository,
            PaymentRepository paymentRepository,
            BookingRepository bookingRepository,
            WalkRepository walkRepository,
            BookingService bookingService,
            MembershipService membershipService,
            CommissionCalculator commissionCalculator,
            @Value("${barkwalk.stripe.webhook-secret:}") String webhookSecret) {
        this.webhookEventRepository = webhookEventRepository;
        this.paymentRepository = paymentRepository;
        this.bookingRepository = bookingRepository;
        this.walkRepository = walkRepository;
        this.bookingService = bookingService;
        this.membershipService = membershipService;
        this.commissionCalculator = commissionCalculator;
        this.webhookSecret = webhookSecret;
    }

    @Transactional
    public WebhookAck handleWebhook(String signature, Map<String, Object> payload) {
        if (webhookSecret != null && !webhookSecret.isBlank()) {
            if (signature == null || signature.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing Stripe signature");
            }
        }
        String eventId = String.valueOf(payload.getOrDefault("id", "dev-" + System.currentTimeMillis()));
        if (webhookEventRepository.existsByProviderAndEventId("stripe", eventId)) {
            return new WebhookAck("duplicate");
        }
        WebhookEvent event = new WebhookEvent();
        event.setProvider("stripe");
        event.setEventId(eventId);
        webhookEventRepository.save(event);

        Object data = payload.get("data");
        if (data instanceof Map<?, ?> dataMap) {
            Object object = dataMap.get("object");
            if (object instanceof Map<?, ?> obj) {
                Object bookingId = obj.get("booking_id");
                if (bookingId != null) {
                    Long id = Long.valueOf(bookingId.toString());
                    // Confirmation uniquement côté serveur ; jamais via retour navigateur.
                    bookingService.confirmPayment(id);
                    recordBookingPayment(id, obj);
                }
                Object membershipId = obj.get("membership_id");
                if (membershipId != null) {
                    Long id = Long.valueOf(membershipId.toString());
                    Membership membership = membershipService.activateOnPayment(id);
                    recordMembershipPayment(membership, obj);
                }
            }
        }
        return new WebhookAck("ok");
    }

    private void recordMembershipPayment(Membership membership, Map<?, ?> stripeObject) {
        if (paymentRepository.findByMembershipId(membership.getId()).isPresent()) {
            return;
        }
        Payment payment = new Payment();
        payment.setUserId(membership.getUserId());
        payment.setMembershipId(membership.getId());
        payment.setAmountCents(0);
        payment.setPaymentType("MEMBERSHIP");
        payment.setStatus("SUCCEEDED");
        Object intent = stripeObject.get("id");
        if (intent != null) {
            payment.setStripePaymentIntent(intent.toString());
        }
        payment.setCommissionCents(0);
        Object amount = stripeObject.get("amount_cents");
        if (amount != null) {
            payment.setAmountCents(Integer.parseInt(amount.toString()));
        }
        paymentRepository.save(payment);
    }

    private void recordBookingPayment(Long bookingId, Map<?, ?> stripeObject) {
        Booking booking = bookingRepository.findById(bookingId).orElse(null);
        if (booking == null || booking.getPriceCents() <= 0) {
            return;
        }
        if (paymentRepository.findByBookingId(bookingId).isPresent()) {
            return;
        }
        int commission = 0;
        Walk walk = walkRepository.findById(booking.getWalkId()).orElse(null);
        if (walk != null && "INDEP_GUIDED".equals(walk.getWalkType())) {
            commission = commissionCalculator.commissionCents(booking.getPriceCents());
        }
        Payment payment = new Payment();
        payment.setUserId(booking.getUserId());
        payment.setBookingId(bookingId);
        payment.setAmountCents(booking.getPriceCents());
        payment.setPaymentType("BOOKING");
        payment.setStatus("SUCCEEDED");
        Object intent = stripeObject.get("id");
        if (intent != null) {
            payment.setStripePaymentIntent(intent.toString());
        }
        payment.setCommissionCents(commission);
        paymentRepository.save(payment);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> mine() {
        return paymentRepository.findByUserIdOrderByCreatedAtDesc(SecurityUtils.currentUserId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> allForAdmin() {
        return paymentRepository.findAll().stream().map(this::toResponse).toList();
    }

    public PaymentResponse toResponse(Payment p) {
        return new PaymentResponse(
                p.getId(),
                p.getBookingId(),
                p.getMembershipId(),
                p.getAmountCents(),
                p.getCurrency(),
                p.getPaymentType(),
                p.getStatus(),
                p.getCreatedAt());
    }
}
