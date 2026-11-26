package com.barkwalk.walk.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
// DTO de catalogue et de création de balade.

public final class WalkDtos {
    private WalkDtos() {}
    public record WalkResponse(
            Long id,
            Long cityId,
            String cityName,
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
            int capacity,
            int bookedCount,
            int priceCents,
            Integer nonMemberPriceCents,
            Long walkerId,
            Long organizerUserId,
            String status,
            List<String> acceptedSizes,
            List<String> acceptedEnergy,
            String minSociabilityDogs,
            String routeHint,
            boolean communityDisclaimerRequired) {}
    public record WalkCreateRequest(
            @NotNull Long cityId,
            @NotNull Long meetingPointId,
            @NotBlank String walkType,
            @NotBlank String title,
            String description,
            @NotNull Instant startsAt,
            @Min(15) int durationMinutes,
            @Min(1) int capacity,
            int priceCents,
            Integer nonMemberPriceCents,
            Long walkerId,
            @NotEmpty List<String> acceptedSizes,
            @NotEmpty List<String> acceptedEnergy,
            String minSociabilityDogs,
            String routeHint,
            boolean communityDisclaimerRequired) {}
}
