package com.barkwalk.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.barkwalk.auth.dto.AuthDtos.LoginRequest;
import com.barkwalk.auth.dto.AuthDtos.RegisterRequest;
import com.barkwalk.auth.dto.AuthDtos.TokenResponse;
import com.barkwalk.auth.jwt.JwtService;
import com.barkwalk.common.Role;
import com.barkwalk.user.domain.Profile;
import com.barkwalk.user.domain.RefreshToken;
import com.barkwalk.user.domain.User;
import com.barkwalk.user.repository.ProfileRepository;
import com.barkwalk.user.repository.RefreshTokenRepository;
import com.barkwalk.user.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private ProfileRepository profileRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthService authService;

    private void stubTokenIssue() {
        when(jwtService.getRefreshTokenDays()).thenReturn(14L);
        when(jwtService.createAccessToken(any(), anyString(), any())).thenReturn("access.jwt");
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // Refuse l'enregistrement sans acceptation des CGU.
    @Test
    void register_rejectsWhenCguNotAccepted() {
        var req = new RegisterRequest("Ada@Example.com", "Secret1!", "Ada", "Lovelace", 1L, false);
        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // Normalise l'email et crée le profil utilisateur.
    @Test
    void register_normalizesEmailAndCreatesProfile() {
        stubTokenIssue();
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.existsByEmailIgnoreCase("Ada@Example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            try {
                var f = User.class.getDeclaredField("id");
                f.setAccessible(true);
                f.set(u, 42L);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            return u;
        });
        when(profileRepository.save(any(Profile.class))).thenAnswer(inv -> inv.getArgument(0));

        TokenResponse tokens =
                authService.register(new RegisterRequest("Ada@Example.com", "Secret1!", "Ada", "Lovelace", 1L, true));

        assertThat(tokens.accessToken()).isEqualTo("access.jwt");
        assertThat(tokens.refreshToken()).isNotBlank();
        ArgumentCaptor<User> userCap = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCap.capture());
        assertThat(userCap.getValue().getEmail()).isEqualTo("ada@example.com");
        verify(profileRepository).save(any(Profile.class));
    }

    // Refuse si l'email existe déjà.
    @Test
    void register_conflictWhenEmailExists() {
        when(userRepository.existsByEmailIgnoreCase("ada@example.com")).thenReturn(true);
        assertThatThrownBy(() -> authService.register(
                        new RegisterRequest("ada@example.com", "Secret1!", "Ada", "Lovelace", 1L, true)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(userRepository, never()).save(any());
    }

    // Retourne les jetons après authentification.
    @Test
    void login_returnsTokens() {
        stubTokenIssue();
        User user = user(7L, "user@example.com", Set.of(Role.USER));
        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));

        TokenResponse tokens = authService.login(new LoginRequest("user@example.com", "Secret1!"));
        assertThat(tokens.tokenType()).isEqualTo("Bearer");
        verify(authenticationManager).authenticate(any());
    }

    // Refuse de rafraîchir un jeton expiré.
    @Test
    void refresh_rejectsExpiredToken() {
        RefreshToken stored = new RefreshToken();
        stored.setUserId(1L);
        stored.setTokenHash("abc");
        stored.setExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS));
        when(refreshTokenRepository.findByTokenHashAndRevokedFalse(anyString())).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> authService.refresh("raw-token"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // Renouvelle et fait pivoter un jeton valide.
    @Test
    void refresh_rotatesValidToken() {
        stubTokenIssue();
        RefreshToken stored = new RefreshToken();
        stored.setUserId(7L);
        stored.setExpiresAt(Instant.now().plus(1, ChronoUnit.DAYS));
        when(refreshTokenRepository.findByTokenHashAndRevokedFalse(anyString())).thenReturn(Optional.of(stored));
        when(userRepository.findById(7L)).thenReturn(Optional.of(user(7L, "user@example.com", Set.of(Role.USER))));

        TokenResponse tokens = authService.refresh("raw-token");
        assertThat(stored.isRevoked()).isTrue();
        assertThat(tokens.accessToken()).isEqualTo("access.jwt");
    }

    // Révoque le jeton lors de la déconnexion.
    @Test
    void logout_revokesWhenPresent() {
        RefreshToken stored = new RefreshToken();
        when(refreshTokenRepository.findByTokenHashAndRevokedFalse(anyString())).thenReturn(Optional.of(stored));
        authService.logout("raw");
        assertThat(stored.isRevoked()).isTrue();
    }

    // Ne lève pas d'erreur pour email inconnu (sécurité).
    @Test
    void magicLink_doesNotThrowWhenUnknownEmail() {
        when(userRepository.findByEmailIgnoreCase("ghost@example.com")).thenReturn(Optional.empty());
        authService.requestMagicLink("ghost@example.com");
        verify(userRepository).findByEmailIgnoreCase("ghost@example.com");
    }

    private static User user(long id, String email, Set<Role> roles) {
        User u = new User();
        try {
            var f = User.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(u, id);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        u.setEmail(email);
        u.setRoles(roles);
        u.setEnabled(true);
        return u;
    }
}
