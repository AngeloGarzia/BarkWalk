package com.barkwalk.payment.service;

import com.barkwalk.booking.service.BookingService;
import com.barkwalk.common.SecurityUtils;
import com.barkwalk.payment.domain.Payment;
import com.barkwalk.payment.domain.WebhookEvent;
import com.barkwalk.payment.dto.PaymentDtos.PaymentResponse;
import com.barkwalk.payment.dto.PaymentDtos.WebhookAck;
import com.barkwalk.payment.repository.PaymentRepository;
import com.barkwalk.payment.repository.WebhookEventRepository;
import java.util.List;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import java.util.Optional;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PaymentService {
    private static final String PROVIDER = "stripe";
    private static final Set<String> PAYMENT_SUCCESS_EVENTS =
            Set.of("checkout.session.completed", "payment_intent.succeeded");
    private static final ObjectMapper JSON = new ObjectMapper();

    private final WebhookEventRepository webhookEventRepository;
    private final PaymentRepository paymentRepository;
    private final BookingService bookingService;
    private final String webhookSecret;

    public PaymentService(
            WebhookEventRepository webhookEventRepository,
            PaymentRepository paymentRepository,
            BookingService bookingService,
            @Value("${barkwalk.stripe.webhook-secret:}") String webhookSecret) {
        this.webhookEventRepository = webhookEventRepository;
        this.paymentRepository = paymentRepository;
        this.bookingService = bookingService;
        this.webhookSecret = webhookSecret;
    }

    @Transactional
    public WebhookAck handleWebhook(String signature, String rawPayload) {
        // Fail closed : sans secret, impossible de distinguer Stripe d'un appel forgé.
        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Stripe webhook not configured");
        }
        if (signature == null || signature.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing Stripe signature");
        }
        Event event;
        try {
            event = Webhook.constructEvent(rawPayload, signature, webhookSecret);
        } catch (SignatureVerificationException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Stripe signature");
        }

        if (webhookEventRepository.existsByProviderAndEventId(PROVIDER, event.getId())) {
            return new WebhookAck("duplicate");
        }
        WebhookEvent webhookEvent = new WebhookEvent();
        webhookEvent.setProvider(PROVIDER);
        webhookEvent.setEventId(event.getId());
        webhookEventRepository.save(webhookEvent);

        if (PAYMENT_SUCCESS_EVENTS.contains(event.getType())) {
            bookingIdFromMetadata(rawPayload).ifPresent(bookingService::confirmPayment);
        }
        return new WebhookAck("ok");
    }

    // La session Checkout / le PaymentIntent doit porter metadata.booking_id (posé à la création).
    private Optional<Long> bookingIdFromMetadata(String rawPayload) {
        JsonNode bookingId = JSON.readTree(rawPayload).path("data").path("object").path("metadata").path("booking_id");
        if (bookingId.isMissingNode() || bookingId.isNull() || bookingId.asString().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(Long.valueOf(bookingId.asString()));
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
