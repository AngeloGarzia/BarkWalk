package com.barkwalk.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;
// DTO profil.

public final class ProfileDtos {
    private ProfileDtos() {}

    public record PhotoResponse(Long id, String url, short sortOrder) {}

    public record ProfileResponse(
            Long userId,
            String email,
            String firstName,
            String lastName,
            String phone,
            String bio,
            Long cityId,
            Long zoneId,
            int completedWalksCount,
            String communityOrgStatus,
            Set<String> roles,
            List<PhotoResponse> photos) {}

    public record ProfileUpdateRequest(
            @NotBlank @Size(max = 80) String firstName,
            @NotBlank @Size(max = 80) String lastName,
            @Size(max = 30) String phone,
            @Size(max = 500) String bio,
            Long cityId,
            Long zoneId) {}
}
