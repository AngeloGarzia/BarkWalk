package com.barkwalk.payment.web;

import com.barkwalk.booking.service.BookingService;
import com.barkwalk.payment.domain.Payment;
import com.barkwalk.payment.domain.WebhookEvent;
import com.barkwalk.payment.repository.PaymentRepository;
import com.barkwalk.payment.repository.WebhookEventRepository;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {
    private final WebhookEventRepository webhookEventRepository;
    private final PaymentRepository paymentRepository;
    private final BookingService bookingService;
    private final String webhookSecret;

    public PaymentController(WebhookEventRepository webhookEventRepository, PaymentRepository paymentRepository,
                             BookingService bookingService,
                             @Value("${barkwalk.stripe.webhook-secret:}") String webhookSecret) {
        this.webhookEventRepository = webhookEventRepository;
        this.paymentRepository = paymentRepository;
        this.bookingService = bookingService;
        this.webhookSecret = webhookSecret;
    }

    @PostMapping("/webhook")
    @Transactional
    public Map<String, String> webhook(
            @RequestHeader(value = "Stripe-Signature", required = false) String signature,
            @RequestBody Map<String, Object> payload) {
        if (webhookSecret != null && !webhookSecret.isBlank()) {
            // Production: verify Stripe signature with webhookSecret.
            if (signature == null || signature.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing Stripe signature");
            }
        }
        String eventId = String.valueOf(payload.getOrDefault("id", "dev-" + System.currentTimeMillis()));
        if (webhookEventRepository.existsByProviderAndEventId("stripe", eventId)) {
            return Map.of("status", "duplicate");
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
                    bookingService.confirmPayment(Long.valueOf(bookingId.toString()));
                }
            }
        }
        return Map.of("status", "ok");
    }

    @GetMapping("/me")
    public java.util.List<Payment> mine() {
        return paymentRepository.findByUserIdOrderByCreatedAtDesc(com.barkwalk.common.SecurityUtils.currentUserId());
    }
}
