package com.barkwalk.user.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "token_hash", nullable = false, unique = true) private String tokenHash;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(nullable = false) private boolean revoked;
    @Column(name = "family_id", nullable = false) private java.util.UUID familyId = java.util.UUID.randomUUID();
    @Column(name = "replaced_by_token_id") private Long replacedByTokenId;
    @Column(name = "reused_at") private Instant reusedAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();
    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public boolean isRevoked() { return revoked; }
    public void setRevoked(boolean revoked) { this.revoked = revoked; }
    public java.util.UUID getFamilyId() { return familyId; }
    public void setFamilyId(java.util.UUID familyId) { this.familyId = familyId; }
    public Long getReplacedByTokenId() { return replacedByTokenId; }
    public void setReplacedByTokenId(Long replacedByTokenId) { this.replacedByTokenId = replacedByTokenId; }
    public Instant getReusedAt() { return reusedAt; }
    public void setReusedAt(Instant reusedAt) { this.reusedAt = reusedAt; }
}
