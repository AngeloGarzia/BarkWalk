package com.barkwalk.dog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

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

    public record PhotoResponse(Long id, String url, short sortOrder) {}

    public record DogResponse(
            Long id, String name, String breed, LocalDate birthDate, String sex, boolean sterilized,
            BigDecimal weightKg, String sizeCategory, String identificationNumber, String energyLevel,
            String sociabilityDogs, String sociabilityHumans, String recallLevel, String leashPulling,
            String attentionNotes, String dangerCategory, String dossierStatus, String refusalReason,
            Instant createdAt, List<PhotoResponse> photos
    ) {}

    public record DocumentRequest(@NotBlank String docType, @NotBlank String filename, LocalDate expiresAt) {}
    public record DocumentResponse(Long id, String docType, String signedUrl, String uploadUrl, LocalDate expiresAt, String status) {}
    public record RefuseRequest(@NotBlank String reason) {}
}
