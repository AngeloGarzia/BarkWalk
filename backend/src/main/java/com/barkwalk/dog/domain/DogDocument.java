package com.barkwalk.dog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

// Pièce jointe du dossier (vaccin, assurance, etc.), stockée hors base.
@Entity
@Table(name = "dog_documents")
public class DogDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dog_id", nullable = false)
    private Long dogId;

    @Column(name = "doc_type", nullable = false)
    private String docType;

    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Column(name = "expires_at")
    private LocalDate expiresAt;

    @Column(nullable = false)
    private String status = "PENDING";

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt = Instant.now();

    @Column(name = "reminder_j30_sent_at")
    private Instant reminderJ30SentAt;

    @Column(name = "reminder_j7_sent_at")
    private Instant reminderJ7SentAt;

    public Long getId() {
        return id;
    }

    public Long getDogId() {
        return dogId;
    }

    public void setDogId(Long dogId) {
        this.dogId = dogId;
    }

    public String getDocType() {
        return docType;
    }

    public void setDocType(String docType) {
        this.docType = docType;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public void setStorageKey(String storageKey) {
        this.storageKey = storageKey;
    }

    public LocalDate getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDate expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public Instant getReminderJ30SentAt() {
        return reminderJ30SentAt;
    }

    public void setReminderJ30SentAt(Instant reminderJ30SentAt) {
        this.reminderJ30SentAt = reminderJ30SentAt;
    }

    public Instant getReminderJ7SentAt() {
        return reminderJ7SentAt;
    }

    public void setReminderJ7SentAt(Instant reminderJ7SentAt) {
        this.reminderJ7SentAt = reminderJ7SentAt;
    }
}
