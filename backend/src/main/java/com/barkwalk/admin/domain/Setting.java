package com.barkwalk.admin.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "settings")
public class Setting {
    @Id private String key;
    @Column(nullable = false) private String value;
    private String description;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt = Instant.now();
    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
