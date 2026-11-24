package com.barkwalk.dog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "dogs")
public class Dog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String breed;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(nullable = false)
    private String sex;

    @Column(nullable = false)
    private boolean sterilized;

    @Column(name = "weight_kg")
    private BigDecimal weightKg;

    @Column(name = "size_category", nullable = false)
    private String sizeCategory;

    @Column(name = "identification_number")
    private String identificationNumber;

    @Column(name = "photo_key")
    private String photoKey;

    @Column(name = "energy_level", nullable = false)
    private String energyLevel;

    @Column(name = "sociability_dogs", nullable = false)
    private String sociabilityDogs;

    @Column(name = "sociability_humans", nullable = false)
    private String sociabilityHumans;

    @Column(name = "recall_level")
    private String recallLevel;

    @Column(name = "leash_pulling")
    private String leashPulling;

    @Column(name = "attention_notes")
    private String attentionNotes;

    @Column(name = "danger_category")
    private String dangerCategory;

    @Column(name = "dossier_status", nullable = false)
    private String dossierStatus = "DRAFT";

    @Column(name = "refusal_reason")
    private String refusalReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Long getId() {
        return id;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBreed() {
        return breed;
    }

    public void setBreed(String breed) {
        this.breed = breed;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }

    public String getSex() {
        return sex;
    }

    public void setSex(String sex) {
        this.sex = sex;
    }

    public boolean isSterilized() {
        return sterilized;
    }

    public void setSterilized(boolean sterilized) {
        this.sterilized = sterilized;
    }

    public BigDecimal getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(BigDecimal weightKg) {
        this.weightKg = weightKg;
    }

    public String getSizeCategory() {
        return sizeCategory;
    }

    public void setSizeCategory(String sizeCategory) {
        this.sizeCategory = sizeCategory;
    }

    public String getIdentificationNumber() {
        return identificationNumber;
    }

    public void setIdentificationNumber(String identificationNumber) {
        this.identificationNumber = identificationNumber;
    }

    public String getPhotoKey() {
        return photoKey;
    }

    public void setPhotoKey(String photoKey) {
        this.photoKey = photoKey;
    }

    public String getEnergyLevel() {
        return energyLevel;
    }

    public void setEnergyLevel(String energyLevel) {
        this.energyLevel = energyLevel;
    }

    public String getSociabilityDogs() {
        return sociabilityDogs;
    }

    public void setSociabilityDogs(String sociabilityDogs) {
        this.sociabilityDogs = sociabilityDogs;
    }

    public String getSociabilityHumans() {
        return sociabilityHumans;
    }

    public void setSociabilityHumans(String sociabilityHumans) {
        this.sociabilityHumans = sociabilityHumans;
    }

    public String getRecallLevel() {
        return recallLevel;
    }

    public void setRecallLevel(String recallLevel) {
        this.recallLevel = recallLevel;
    }

    public String getLeashPulling() {
        return leashPulling;
    }

    public void setLeashPulling(String leashPulling) {
        this.leashPulling = leashPulling;
    }

    public String getAttentionNotes() {
        return attentionNotes;
    }

    public void setAttentionNotes(String attentionNotes) {
        this.attentionNotes = attentionNotes;
    }

    public String getDangerCategory() {
        return dangerCategory;
    }

    public void setDangerCategory(String dangerCategory) {
        this.dangerCategory = dangerCategory;
    }

    public String getDossierStatus() {
        return dossierStatus;
    }

    public void setDossierStatus(String dossierStatus) {
        this.dossierStatus = dossierStatus;
    }

    public String getRefusalReason() {
        return refusalReason;
    }

    public void setRefusalReason(String refusalReason) {
        this.refusalReason = refusalReason;
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
