package com.barkwalk.payment.dto;

import java.time.Instant;

public final class PaymentDtos {
    private PaymentDtos() {}

    public record PaymentResponse(
            Long id,
            Long bookingId,
            Long membershipId,
            int amountCents,
            String currency,
            String paymentType,
            String status,
            Instant createdAt) {}

    public record WebhookAck(String status) {}
}
