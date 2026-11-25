package com.barkwalk.walker.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

// Profil promeneur (salarié ou indépendant).
@Entity
@Table(name = "walker_profiles")
public class WalkerProfile {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false, unique = true) private Long userId;
    @Column(name = "walker_type", nullable = false) private String walkerType;
    @JsonIgnore private String siret;
    @JsonIgnore @Column(name = "diploma_key") private String diplomaKey;
    @JsonIgnore @Column(name = "rc_pro_key") private String rcProKey;
    @Column(name = "rc_pro_expires_at") private LocalDate rcProExpiresAt;
    @JsonIgnore @Column(name = "stripe_connect_account") private String stripeConnectAccount;
    @Column(name = "charter_signed_at") private Instant charterSignedAt;
    @Column(nullable = false) private String status = "PENDING";
    private String bio;
    @Column(name = "average_rating") private BigDecimal averageRating;
    @Column(name = "ratings_count", nullable = false) private int ratingsCount;
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();
    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getWalkerType() { return walkerType; }
    public void setWalkerType(String walkerType) { this.walkerType = walkerType; }
    public String getSiret() { return siret; }
    public void setSiret(String siret) { this.siret = siret; }
    public String getDiplomaKey() { return diplomaKey; }
    public void setDiplomaKey(String diplomaKey) { this.diplomaKey = diplomaKey; }
    public String getRcProKey() { return rcProKey; }
    public void setRcProKey(String rcProKey) { this.rcProKey = rcProKey; }
    public LocalDate getRcProExpiresAt() { return rcProExpiresAt; }
    public void setRcProExpiresAt(LocalDate rcProExpiresAt) { this.rcProExpiresAt = rcProExpiresAt; }
    public String getStripeConnectAccount() { return stripeConnectAccount; }
    public void setStripeConnectAccount(String v) { this.stripeConnectAccount = v; }
    public Instant getCharterSignedAt() { return charterSignedAt; }
    public void setCharterSignedAt(Instant charterSignedAt) { this.charterSignedAt = charterSignedAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }
    public BigDecimal getAverageRating() { return averageRating; }
    public void setAverageRating(BigDecimal averageRating) { this.averageRating = averageRating; }
    public int getRatingsCount() { return ratingsCount; }
    public void setRatingsCount(int ratingsCount) { this.ratingsCount = ratingsCount; }
}
