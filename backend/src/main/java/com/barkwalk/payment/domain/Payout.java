package com.barkwalk.payment.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "payouts")
public class Payout {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "walker_id", nullable = false) private Long walkerId;
    @Column(name = "payment_id", nullable = false) private Long paymentId;
    @Column(name = "amount_cents", nullable = false) private int amountCents;
    @Column(nullable = false) private String status;
    @Column(name = "stripe_transfer_id") private String stripeTransferId;
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();
    public Long getId() { return id; }
    public Long getWalkerId() { return walkerId; }
    public void setWalkerId(Long walkerId) { this.walkerId = walkerId; }
    public Long getPaymentId() { return paymentId; }
    public void setPaymentId(Long paymentId) { this.paymentId = paymentId; }
    public int getAmountCents() { return amountCents; }
    public void setAmountCents(int amountCents) { this.amountCents = amountCents; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getStripeTransferId() { return stripeTransferId; }
    public void setStripeTransferId(String stripeTransferId) { this.stripeTransferId = stripeTransferId; }
}
