package com.barkwalk.messaging.domain;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;

// Participant à une conversation, avec horodatage de lecture.
@Entity
@Table(name = "conversation_members")
@IdClass(ConversationMember.Pk.class)
public class ConversationMember {
    @Id @Column(name = "conversation_id") private Long conversationId;
    @Id @Column(name = "user_id") private Long userId;
    public Long getConversationId() { return conversationId; }
    public void setConversationId(Long conversationId) { this.conversationId = conversationId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public static class Pk implements Serializable {
        private Long conversationId;
        private Long userId;
        public Long getConversationId() { return conversationId; }
        public void setConversationId(Long conversationId) { this.conversationId = conversationId; }
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public boolean equals(Object o) {
            if (!(o instanceof Pk pk)) return false;
            return Objects.equals(conversationId, pk.conversationId) && Objects.equals(userId, pk.userId);
        }
        public int hashCode() { return Objects.hash(conversationId, userId); }
    }
}
