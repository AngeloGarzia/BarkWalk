package com.barkwalk.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Set;
// Contrats JSON d'entrée et de sortie pour l'authentification.

public final class AuthDtos {
    private AuthDtos() {}

    public record RegisterRequest(
            @Email @NotBlank String email,
            @NotBlank @Size(min = 8, max = 100) String password,
            @NotBlank @Size(max = 80) String firstName,
            @NotBlank @Size(max = 80) String lastName,
            Long cityId,
            boolean acceptCgu
    ) {}

    public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {}

    public record TokenResponse(String accessToken, String refreshToken, String tokenType, long expiresInMinutes, Set<String> roles) {}

    public record RefreshRequest(@NotBlank String refreshToken) {}

    public record MagicLinkRequest(@Email @NotBlank String email) {}
}
