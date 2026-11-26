package com.barkwalk.booking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

// Réservation d'un chien sur une balade (statuts, essai, annulation, expiration paiement).
@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "walk_id", nullable = false)
    private Long walkId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "dog_id", nullable = false)
    private Long dogId;

    @Column(nullable = false)
    private String status;

    @Column(name = "payment_required", nullable = false)
    private boolean paymentRequired;

    @Column(name = "price_cents", nullable = false)
    private int priceCents;

    @Column(name = "community_ack", nullable = false)
    private boolean communityAck;

    @Column(name = "is_trial", nullable = false)
    private boolean trial;

    @Column(name = "stripe_session_id")
    private String stripeSessionId;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "payment_expires_at")
    private Instant paymentExpiresAt;

    @Column(name = "cancelled_by_user_id")
    private Long cancelledByUserId;

    @Column(name = "cancelled_by_role")
    private String cancelledByRole;

    @Column(name = "cancellation_reason")
    private String cancellationReason;

    @Column(name = "counts_toward_org_threshold", nullable = false)
    private boolean countsTowardOrgThreshold;

    @Column(name = "counts_toward_membership_quota", nullable = false)
    private boolean countsTowardMembershipQuota;

    /** R16 : réservation future à traiter après expiration du dossier chien. */
    @Column(name = "admin_alert", nullable = false)
    private boolean adminAlert;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Long getId() {
        return id;
    }

    public Long getWalkId() {
        return walkId;
    }

    public void setWalkId(Long walkId) {
        this.walkId = walkId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getDogId() {
        return dogId;
    }

    public void setDogId(Long dogId) {
        this.dogId = dogId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isPaymentRequired() {
        return paymentRequired;
    }

    public void setPaymentRequired(boolean paymentRequired) {
        this.paymentRequired = paymentRequired;
    }

    public int getPriceCents() {
        return priceCents;
    }

    public void setPriceCents(int priceCents) {
        this.priceCents = priceCents;
    }

    public boolean isCommunityAck() {
        return communityAck;
    }

    public void setCommunityAck(boolean communityAck) {
        this.communityAck = communityAck;
    }

    public boolean isTrial() {
        return trial;
    }

    public void setTrial(boolean trial) {
        this.trial = trial;
    }

    public String getStripeSessionId() {
        return stripeSessionId;
    }

    public void setStripeSessionId(String stripeSessionId) {
        this.stripeSessionId = stripeSessionId;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(Instant confirmedAt) {
        this.confirmedAt = confirmedAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(Instant cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPaymentExpiresAt() {
        return paymentExpiresAt;
    }

    public void setPaymentExpiresAt(Instant paymentExpiresAt) {
        this.paymentExpiresAt = paymentExpiresAt;
    }

    public Long getCancelledByUserId() {
        return cancelledByUserId;
    }

    public void setCancelledByUserId(Long cancelledByUserId) {
        this.cancelledByUserId = cancelledByUserId;
    }

    public String getCancelledByRole() {
        return cancelledByRole;
    }

    public void setCancelledByRole(String cancelledByRole) {
        this.cancelledByRole = cancelledByRole;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public void setCancellationReason(String cancellationReason) {
        this.cancellationReason = cancellationReason;
    }

    public boolean isCountsTowardOrgThreshold() {
        return countsTowardOrgThreshold;
    }

    public void setCountsTowardOrgThreshold(boolean countsTowardOrgThreshold) {
        this.countsTowardOrgThreshold = countsTowardOrgThreshold;
    }

    public boolean isCountsTowardMembershipQuota() {
        return countsTowardMembershipQuota;
    }

    public void setCountsTowardMembershipQuota(boolean countsTowardMembershipQuota) {
        this.countsTowardMembershipQuota = countsTowardMembershipQuota;
    }

    public boolean isAdminAlert() {
        return adminAlert;
    }

    public void setAdminAlert(boolean adminAlert) {
        this.adminAlert = adminAlert;
    }
}
