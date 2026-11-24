package com.barkwalk.user.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class ProfileDtos {
    private ProfileDtos() {}
    public record ProfileResponse(Long userId, String email, String firstName, String lastName, String phone,
                                  String bio, Long cityId, Long zoneId, int completedWalksCount,
                                  boolean communityOrgEnabled, java.util.Set<String> roles) {}
    public record ProfileUpdateRequest(@NotBlank @Size(max = 80) String firstName,
                                       @NotBlank @Size(max = 80) String lastName,
                                       @Size(max = 30) String phone,
                                       @Size(max = 500) String bio,
                                       Long cityId, Long zoneId) {}
}
