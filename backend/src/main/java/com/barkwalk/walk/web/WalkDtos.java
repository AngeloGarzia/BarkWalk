package com.barkwalk.walk.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public final class WalkDtos {
    private WalkDtos() {}
    public record WalkResponse(Long id, Long cityId, Long meetingPointId, String walkType, String title,
            String description, Instant startsAt, int durationMinutes, int capacity, int bookedCount,
            int priceCents, Integer nonMemberPriceCents, Long walkerId, Long organizerUserId, String status,
            String acceptedSizes, String acceptedEnergy, String routeHint, boolean communityDisclaimerRequired) {}
    public record WalkCreateRequest(
            @NotNull Long cityId, @NotNull Long meetingPointId, @NotBlank String walkType,
            @NotBlank String title, String description, @NotNull Instant startsAt,
            @Min(15) int durationMinutes, @Min(1) int capacity, int priceCents, Integer nonMemberPriceCents,
            Long walkerId, @NotBlank String acceptedSizes, @NotBlank String acceptedEnergy,
            String minSociabilityDogs, String routeHint, boolean communityDisclaimerRequired) {}
}
