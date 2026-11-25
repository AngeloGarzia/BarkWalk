package com.barkwalk.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

// Profil nominal (coordonnées, ville, statut organisateur communautaire).
@Entity
@Table(name = "profiles")
public class Profile {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    private String phone;
    private String bio;

    @Column(name = "photo_key")
    private String photoKey;

    @Column(name = "city_id")
    private Long cityId;

    @Column(name = "zone_id")
    private Long zoneId;

    @Column(name = "community_org_status", nullable = false)
    private String communityOrgStatus = "NONE";

    @Column(name = "completed_walks_count", nullable = false)
    private int completedWalksCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public String getPhotoKey() {
        return photoKey;
    }

    public void setPhotoKey(String photoKey) {
        this.photoKey = photoKey;
    }

    public Long getCityId() {
        return cityId;
    }

    public void setCityId(Long cityId) {
        this.cityId = cityId;
    }

    public Long getZoneId() {
        return zoneId;
    }

    public void setZoneId(Long zoneId) {
        this.zoneId = zoneId;
    }

    public String getCommunityOrgStatus() {
        return communityOrgStatus;
    }

    public void setCommunityOrgStatus(String communityOrgStatus) {
        this.communityOrgStatus = communityOrgStatus;
    }

    public boolean isCommunityOrgActive() {
        return "ACTIVE".equals(communityOrgStatus);
    }

    public boolean isCommunityOrgRevoked() {
        return "REVOKED".equals(communityOrgStatus);
    }

    public int getCompletedWalksCount() {
        return completedWalksCount;
    }

    public void setCompletedWalksCount(int completedWalksCount) {
        this.completedWalksCount = completedWalksCount;
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
