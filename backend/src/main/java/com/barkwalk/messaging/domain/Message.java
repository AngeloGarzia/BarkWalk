package com.barkwalk.messaging.domain;

import jakarta.persistence.*;
import java.time.Instant;

// Message texte ou média dans une conversation.
@Entity
@Table(name = "messages")
public class Message {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "conversation_id", nullable = false) private Long conversationId;
    @Column(name = "sender_id", nullable = false) private Long senderId;
    private String body;
    @Column(name = "photo_key") private String photoKey;
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();
    @Column(name = "read_at") private Instant readAt;
    public Long getId() { return id; }
    public Long getConversationId() { return conversationId; }
    public void setConversationId(Long conversationId) { this.conversationId = conversationId; }
    public Long getSenderId() { return senderId; }
    public void setSenderId(Long senderId) { this.senderId = senderId; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getPhotoKey() { return photoKey; }
    public void setPhotoKey(String photoKey) { this.photoKey = photoKey; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getReadAt() { return readAt; }
    public void setReadAt(Instant readAt) { this.readAt = readAt; }
}
