package com.barkwalk.walk.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;
// DTO de catalogue et de création de balade.

public final class WalkDtos {
    private WalkDtos() {}
    public record WalkResponse(
            Long id,
            Long cityId,
            String cityName,
            String department,
            String region,
            Long meetingPointId,
            String meetingPointName,
            String meetingPointAddress,
            Double lat,
            Double lng,
            String walkType,
            String title,
            String description,
            Instant startsAt,
            int durationMinutes,
            int elevationGainM,
            String terrainType,
            int difficultyLevel,
            int capacity,
            int bookedCount,
            int priceCents,
            Integer nonMemberPriceCents,
            Long walkerId,
            Long organizerUserId,
            String organizerDisplayName,
            String status,
            Instant startedAt,
            Instant completedAt,
            Instant archivedAt,
            Long recycledFromWalkId,
            List<String> promoPhotoUrls,
            int galleryCount,
            List<String> acceptedSizes,
            List<String> acceptedEnergy,
            String minSociabilityDogs,
            boolean allowsDangerCategories,
            String routeHint,
            Map<String, Object> routeGeojson,
            boolean communityDisclaimerRequired) {}

    public record WalkRecycleRequest(@NotNull Instant startsAt) {}
    public record WalkCreateRequest(
            @NotNull Long cityId,
            @NotNull Long meetingPointId,
            @NotBlank String walkType,
            @NotBlank String title,
            String description,
            @NotNull Instant startsAt,
            @Min(15) int durationMinutes,
            @Min(0) @Max(3000) Integer elevationGainM,
            String terrainType,
            @Min(1) int capacity,
            int priceCents,
            Integer nonMemberPriceCents,
            Long walkerId,
            @NotEmpty List<String> acceptedSizes,
            @NotEmpty List<String> acceptedEnergy,
            String minSociabilityDogs,
            boolean allowsDangerCategories,
            String routeHint,
            Map<String, Object> routeGeojson,
            boolean communityDisclaimerRequired) {}

    public record WalkUpdateRequest(
            @NotNull Long meetingPointId,
            @NotBlank String title,
            String description,
            @NotNull Instant startsAt,
            @Min(15) int durationMinutes,
            @Min(0) @Max(3000) Integer elevationGainM,
            String terrainType,
            @Min(1) int capacity,
            @NotEmpty List<String> acceptedSizes,
            @NotEmpty List<String> acceptedEnergy,
            String minSociabilityDogs,
            String routeHint,
            Map<String, Object> routeGeojson) {}
}
