package com.barkwalk.attendance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

// Compte-rendu global d'une balade (les incidents vivent dans une table dédiée).
@Entity
@Table(name = "walk_reports")
public class WalkReport {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "walk_id", nullable = false, unique = true)
    private Long walkId;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Column(name = "actual_duration_minutes")
    private Integer actualDurationMinutes;

    @Column(name = "route_done")
    private String routeDone;

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

    public Long getAuthorId() {
        return authorId;
    }

    public void setAuthorId(Long authorId) {
        this.authorId = authorId;
    }

    public Integer getActualDurationMinutes() {
        return actualDurationMinutes;
    }

    public void setActualDurationMinutes(Integer actualDurationMinutes) {
        this.actualDurationMinutes = actualDurationMinutes;
    }

    public String getRouteDone() {
        return routeDone;
    }

    public void setRouteDone(String routeDone) {
        this.routeDone = routeDone;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
