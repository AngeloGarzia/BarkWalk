package com.barkwalk.attendance.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "walk_reports")
public class WalkReport {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "walk_id", nullable = false, unique = true) private Long walkId;
    @Column(name = "author_id", nullable = false) private Long authorId;
    @Column(name = "actual_duration_minutes") private Integer actualDurationMinutes;
    @Column(name = "route_done") private String routeDone;
    @Column(name = "incident_type") private String incidentType;
    @Column(name = "incident_description") private String incidentDescription;
    @Column(name = "incident_severity") private String incidentSeverity;
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();
    public Long getId() { return id; }
    public Long getWalkId() { return walkId; }
    public void setWalkId(Long walkId) { this.walkId = walkId; }
    public Long getAuthorId() { return authorId; }
    public void setAuthorId(Long authorId) { this.authorId = authorId; }
    public Integer getActualDurationMinutes() { return actualDurationMinutes; }
    public void setActualDurationMinutes(Integer v) { this.actualDurationMinutes = v; }
    public String getRouteDone() { return routeDone; }
    public void setRouteDone(String routeDone) { this.routeDone = routeDone; }
    public String getIncidentType() { return incidentType; }
    public void setIncidentType(String incidentType) { this.incidentType = incidentType; }
    public String getIncidentDescription() { return incidentDescription; }
    public void setIncidentDescription(String incidentDescription) { this.incidentDescription = incidentDescription; }
    public String getIncidentSeverity() { return incidentSeverity; }
    public void setIncidentSeverity(String incidentSeverity) { this.incidentSeverity = incidentSeverity; }
    public Instant getCreatedAt() { return createdAt; }
}
