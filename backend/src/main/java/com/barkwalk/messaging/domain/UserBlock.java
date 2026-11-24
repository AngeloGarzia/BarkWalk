package com.barkwalk.messaging.domain;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "user_blocks")
@IdClass(UserBlock.Pk.class)
public class UserBlock {
    @Id @Column(name = "blocker_id") private Long blockerId;
    @Id @Column(name = "blocked_id") private Long blockedId;
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();
    public Long getBlockerId() { return blockerId; }
    public void setBlockerId(Long blockerId) { this.blockerId = blockerId; }
    public Long getBlockedId() { return blockedId; }
    public void setBlockedId(Long blockedId) { this.blockedId = blockedId; }
    public static class Pk implements Serializable {
        private Long blockerId;
        private Long blockedId;
        public Long getBlockerId() { return blockerId; }
        public void setBlockerId(Long blockerId) { this.blockerId = blockerId; }
        public Long getBlockedId() { return blockedId; }
        public void setBlockedId(Long blockedId) { this.blockedId = blockedId; }
        public boolean equals(Object o) {
            if (!(o instanceof Pk pk)) return false;
            return Objects.equals(blockerId, pk.blockerId) && Objects.equals(blockedId, pk.blockedId);
        }
        public int hashCode() { return Objects.hash(blockerId, blockedId); }
    }
}
