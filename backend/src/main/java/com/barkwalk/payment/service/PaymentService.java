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
                    bookingService.confirmPayment(Long.valueOf(bookingId.toString()));
                }
            }
        }
        return new WebhookAck("ok");
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
