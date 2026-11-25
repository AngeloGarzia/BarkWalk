package com.barkwalk.city.dto;

public final class CityDtos {
    private CityDtos() {}

    public record CityResponse(
            Long id,
            String inseeCode,
            String postalCode,
            String name,
            String department,
            String region,
            Double lat,
            Double lng,
            boolean active) {}

    public record ZoneResponse(Long id, Long cityId, String name) {}
}
