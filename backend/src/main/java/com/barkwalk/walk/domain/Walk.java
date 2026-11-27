package com.barkwalk.walk.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

// Balade planifiée : lieu, créneau, capacité, filtres chiens, encadrant ou organisateur.
@Entity
@Table(name = "walks")
public class Walk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "city_id", nullable = false)
    private Long cityId;

    @Column(name = "meeting_point_id", nullable = false)
    private Long meetingPointId;

    @Column(name = "walk_type", nullable = false)
    private String walkType;

    @Column(nullable = false)
    private String title;

    private String description;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    /** Dénivelé positif approximatif (mètres). */
    @Column(name = "elevation_gain_m", nullable = false)
    private int elevationGainM;

    /** FLAT | PARK | FOREST | TRAIL | MOUNTAIN */
    @Column(name = "terrain_type", nullable = false)
    private String terrainType = "FLAT";

    /** Score 1–10 dérivé durée + dénivelé + terrain. */
    @Column(name = "difficulty_level", nullable = false)
    private int difficultyLevel = 1;

    @Column(nullable = false)
    private int capacity;

    @Column(name = "price_cents", nullable = false)
    private int priceCents;

    @Column(name = "non_member_price_cents")
    private Integer nonMemberPriceCents;

    @Column(name = "walker_id")
    private Long walkerId;

    @Column(name = "organizer_user_id")
    private Long organizerUserId;

    @Column(nullable = false)
    private String status = "PUBLISHED";

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "accepted_sizes", nullable = false, columnDefinition = "text[]")
    private String[] acceptedSizes;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "accepted_energy", nullable = false, columnDefinition = "text[]")
    private String[] acceptedEnergy;

    @Column(name = "min_sociability_dogs")
    private String minSociabilityDogs;

    /** Balade dédiée chiens catégorie 1/2. */
    @Column(name = "allows_danger_categories", nullable = false)
    private boolean allowsDangerCategories;

    @Column(name = "route_hint")
    private String routeHint;

    /** GeoJSON LineString (coordinates [lng, lat]). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "route_geojson", columnDefinition = "jsonb")
    private Map<String, Object> routeGeojson;

    @Column(name = "cancelled_by_user_id")
    private Long cancelledByUserId;

    @Column(name = "cancellation_reason")
    private String cancellationReason;

    /** Présente dès qu'une balade est terminée, annulée ou archivée manuellement. */
    @Column(name = "archived_at")
    private Instant archivedAt;

    /** Pointage des présents validé par orga / encadrant / admin. */
    @Column(name = "attendance_validated_at")
    private Instant attendanceValidatedAt;

    /** Balade passée dont celle-ci est un recyclage. */
    @Column(name = "recycled_from_walk_id")
    private Long recycledFromWalkId;

    @Column(name = "recurrence_rule_id")
    private Long recurrenceRuleId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Long getId() {
        return id;
    }

    public Long getCityId() {
        return cityId;
    }

    public void setCityId(Long cityId) {
        this.cityId = cityId;
    }

    public Long getMeetingPointId() {
        return meetingPointId;
    }

    public void setMeetingPointId(Long meetingPointId) {
        this.meetingPointId = meetingPointId;
    }

    public String getWalkType() {
        return walkType;
    }

    public void setWalkType(String walkType) {
        this.walkType = walkType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public void setStartsAt(Instant startsAt) {
        this.startsAt = startsAt;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(int durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public int getElevationGainM() {
        return elevationGainM;
    }

    public void setElevationGainM(int elevationGainM) {
        this.elevationGainM = elevationGainM;
    }

    public String getTerrainType() {
        return terrainType;
    }

    public void setTerrainType(String terrainType) {
        this.terrainType = terrainType;
    }

    public int getDifficultyLevel() {
        return difficultyLevel;
    }

    public void setDifficultyLevel(int difficultyLevel) {
        this.difficultyLevel = difficultyLevel;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public int getPriceCents() {
        return priceCents;
    }

    public void setPriceCents(int priceCents) {
        this.priceCents = priceCents;
    }

    public Integer getNonMemberPriceCents() {
        return nonMemberPriceCents;
    }

    public void setNonMemberPriceCents(Integer nonMemberPriceCents) {
        this.nonMemberPriceCents = nonMemberPriceCents;
    }

    public Long getWalkerId() {
        return walkerId;
    }

    public void setWalkerId(Long walkerId) {
        this.walkerId = walkerId;
    }

    public Long getOrganizerUserId() {
        return organizerUserId;
    }

    public void setOrganizerUserId(Long organizerUserId) {
        this.organizerUserId = organizerUserId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public String[] getAcceptedSizes() {
        return acceptedSizes;
    }

    public void setAcceptedSizes(String[] acceptedSizes) {
        this.acceptedSizes = acceptedSizes;
    }

    public String[] getAcceptedEnergy() {
        return acceptedEnergy;
    }

    public void setAcceptedEnergy(String[] acceptedEnergy) {
        this.acceptedEnergy = acceptedEnergy;
    }

    public String getMinSociabilityDogs() {
        return minSociabilityDogs;
    }

    public void setMinSociabilityDogs(String minSociabilityDogs) {
        this.minSociabilityDogs = minSociabilityDogs;
    }

    public boolean isAllowsDangerCategories() {
        return allowsDangerCategories;
    }

    public void setAllowsDangerCategories(boolean allowsDangerCategories) {
        this.allowsDangerCategories = allowsDangerCategories;
    }

    public String getRouteHint() {
        return routeHint;
    }

    public void setRouteHint(String routeHint) {
        this.routeHint = routeHint;
    }

    public Map<String, Object> getRouteGeojson() {
        return routeGeojson;
    }

    public void setRouteGeojson(Map<String, Object> routeGeojson) {
        this.routeGeojson = routeGeojson;
    }

    // Disclaimer communautaire : dérivé du type, plus de colonne dédiée en base.
    public boolean isCommunityDisclaimerRequired() {
        return "COMMUNITY".equals(walkType);
    }

    public Long getCancelledByUserId() {
        return cancelledByUserId;
    }

    public void setCancelledByUserId(Long cancelledByUserId) {
        this.cancelledByUserId = cancelledByUserId;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public void setCancellationReason(String cancellationReason) {
        this.cancellationReason = cancellationReason;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public void setArchivedAt(Instant archivedAt) {
        this.archivedAt = archivedAt;
    }

    public Instant getAttendanceValidatedAt() {
        return attendanceValidatedAt;
    }

    public void setAttendanceValidatedAt(Instant attendanceValidatedAt) {
        this.attendanceValidatedAt = attendanceValidatedAt;
    }

    public Long getRecycledFromWalkId() {
        return recycledFromWalkId;
    }

    public void setRecycledFromWalkId(Long recycledFromWalkId) {
        this.recycledFromWalkId = recycledFromWalkId;
    }

    public Long getRecurrenceRuleId() {
        return recurrenceRuleId;
    }

    public void setRecurrenceRuleId(Long recurrenceRuleId) {
        this.recurrenceRuleId = recurrenceRuleId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
