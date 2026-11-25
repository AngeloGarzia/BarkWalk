package com.barkwalk.walker.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public final class WalkerDtos {
    private WalkerDtos() {}

    public record ApplyRequest(
            @NotBlank String walkerType,
            String siret,
            String diplomaFilename,
            String rcProFilename,
            String bio) {}

    public record WalkerProfileResponse(
            Long id,
            Long userId,
            String walkerType,
            String status,
            String bio,
            BigDecimal averageRating,
            int ratingsCount,
            LocalDate rcProExpiresAt,
            Instant charterSignedAt,
            boolean hasDiploma,
            boolean hasRcPro) {}

    public record TodayWalkResponse(
            Long id,
            String title,
            Instant startsAt,
            int durationMinutes,
            String address,
            String status,
            long dogCount) {}
}
