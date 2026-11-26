package com.barkwalk.payment.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.Instant;

// Mouvement financier (réservation ou adhésion). Le cœur du montant n'est pas modifiable après coup.
@Entity
@Table(name = "payments")
public class Payment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "booking_id") private Long bookingId;
    @Column(name = "membership_id") private Long membershipId;
    @Column(name = "amount_cents", nullable = false) private int amountCents;
    @Column(nullable = false) private String currency = "EUR";
    @Column(name = "payment_type", nullable = false) private String paymentType;
    @Column(nullable = false) private String status;
    @JsonIgnore @Column(name = "stripe_payment_intent") private String stripePaymentIntent;
    @JsonIgnore @Column(name = "commission_cents", nullable = false) private int commissionCents;
    @Column(name = "refund_amount_cents") private Integer refundAmountCents;
    @Column(name = "refund_provider_id") private String refundProviderId;
    @Column(name = "refunded_at") private Instant refundedAt;
    @Column(name = "refund_reason") private String refundReason;
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();
    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getBookingId() { return bookingId; }
    public void setBookingId(Long bookingId) { this.bookingId = bookingId; }
    public Long getMembershipId() { return membershipId; }
    public void setMembershipId(Long membershipId) { this.membershipId = membershipId; }
    public int getAmountCents() { return amountCents; }
    public void setAmountCents(int amountCents) { this.amountCents = amountCents; }
    public String getCurrency() { return currency; }
    public String getPaymentType() { return paymentType; }
    public void setPaymentType(String paymentType) { this.paymentType = paymentType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getStripePaymentIntent() { return stripePaymentIntent; }
    public void setStripePaymentIntent(String v) { this.stripePaymentIntent = v; }
    public int getCommissionCents() { return commissionCents; }
    public void setCommissionCents(int commissionCents) { this.commissionCents = commissionCents; }
    public Integer getRefundAmountCents() { return refundAmountCents; }
    public void setRefundAmountCents(Integer refundAmountCents) { this.refundAmountCents = refundAmountCents; }
    public String getRefundProviderId() { return refundProviderId; }
    public void setRefundProviderId(String refundProviderId) { this.refundProviderId = refundProviderId; }
    public Instant getRefundedAt() { return refundedAt; }
    public void setRefundedAt(Instant refundedAt) { this.refundedAt = refundedAt; }
    public String getRefundReason() { return refundReason; }
    public void setRefundReason(String refundReason) { this.refundReason = refundReason; }
    public Instant getCreatedAt() { return createdAt; }
}
