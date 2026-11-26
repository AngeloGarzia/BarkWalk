package com.barkwalk.booking.domain;

import jakarta.persistence.*;
import java.time.Instant;

// Place en file d'attente lorsqu'une balade est complète.
@Entity
@Table(name = "waitlist_entries")
public class WaitlistEntry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "walk_id", nullable = false) private Long walkId;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "dog_id", nullable = false) private Long dogId;
    @Column(nullable = false) private int position;
    @Column(name = "offered_at") private Instant offeredAt;
    @Column(name = "offer_expires_at") private Instant offerExpiresAt;
    @Column(nullable = false) private String status = "WAITING";
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();
    public Long getId() { return id; }
    public Instant getCreatedAt() { return createdAt; }
    public Long getWalkId() { return walkId; }
    public void setWalkId(Long walkId) { this.walkId = walkId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getDogId() { return dogId; }
    public void setDogId(Long dogId) { this.dogId = dogId; }
    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }
    public Instant getOfferedAt() { return offeredAt; }
    public void setOfferedAt(Instant offeredAt) { this.offeredAt = offeredAt; }
    public Instant getOfferExpiresAt() { return offerExpiresAt; }
    public void setOfferExpiresAt(Instant offerExpiresAt) { this.offerExpiresAt = offerExpiresAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
