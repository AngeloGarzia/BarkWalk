package com.barkwalk.walk.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// DTO points de rendez-vous.
public final class MeetingPointDtos {
    private MeetingPointDtos() {}

    public record MeetingPointResponse(
            Long id,
            Long cityId,
            String name,
            String address,
            Double lat,
            Double lng) {}

    public record MeetingPointCreateRequest(
            @NotNull Long cityId,
            @NotBlank String name,
            @NotBlank String address,
            @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double lat,
            @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double lng) {}
}
