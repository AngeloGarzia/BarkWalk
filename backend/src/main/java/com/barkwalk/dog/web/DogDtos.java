package com.barkwalk.dog.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public final class DogDtos {
    private DogDtos() {}

    public record DogRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 100) String breed,
            LocalDate birthDate,
            @NotBlank String sex,
            boolean sterilized,
            BigDecimal weightKg,
            @NotBlank String sizeCategory,
            String identificationNumber,
            @NotBlank String energyLevel,
            @NotBlank String sociabilityDogs,
            @NotBlank String sociabilityHumans,
            String recallLevel,
            String leashPulling,
            String attentionNotes,
            String dangerCategory
    ) {}

    public record DogResponse(
            Long id, String name, String breed, LocalDate birthDate, String sex, boolean sterilized,
            BigDecimal weightKg, String sizeCategory, String identificationNumber, String energyLevel,
            String sociabilityDogs, String sociabilityHumans, String recallLevel, String leashPulling,
            String attentionNotes, String dangerCategory, String dossierStatus, String refusalReason, Instant createdAt
    ) {}

    public record DocumentRequest(@NotBlank String docType, @NotBlank String storageKey, LocalDate expiresAt) {}
    public record DocumentResponse(Long id, String docType, String storageKey, String signedUrl, LocalDate expiresAt, String status) {}
    public record RefuseRequest(@NotBlank String reason) {}
}
