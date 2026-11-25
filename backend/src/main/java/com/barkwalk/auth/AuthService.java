package com.barkwalk.auth;

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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

// Inscription, connexion, refresh et déconnexion. Les jetons bruts ne sont jamais persistés, seulement
// leur empreinte.
@Service
public class AuthService {
    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public AuthService(
            UserRepository userRepository,
            ProfileRepository profileRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
    }

    // Crée le compte et le profil associé. L'e-mail est normalisé avant stockage.
    @Transactional
    public TokenResponse register(RegisterRequest request) {
        if (!request.acceptCgu()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CGU must be accepted");
        }
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }
        User user = new User();
        user.setEmail(request.email().trim().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setEmailVerified(false);
        user.setEnabled(true);
        user.setRoles(Set.of(Role.USER));
        user = userRepository.save(user);

        Profile profile = new Profile();
        profile.setUserId(user.getId());
        profile.setFirstName(request.firstName());
        profile.setLastName(request.lastName());
        profile.setCityId(request.cityId());
        profile.setCommunityOrgStatus("NONE");
        profileRepository.save(profile);
        return issueTokens(user);
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email().trim().toLowerCase(), request.password()));
        User user = userRepository.findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        return issueTokens(user);
    }

    // Rotation simple : l'ancien refresh est révoqué, un nouveau est émis.
    @Transactional
    public TokenResponse refresh(String refreshTokenRaw) {
        String hash = sha256(refreshTokenRaw);
        RefreshToken stored = refreshTokenRepository.findByTokenHashAndRevokedFalse(hash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));
        if (stored.getExpiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token expired");
        }
        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User missing"));
        stored.setRevoked(true);
        return issueTokens(user);
    }

    @Transactional
    public void logout(String refreshTokenRaw) {
        refreshTokenRepository.findByTokenHashAndRevokedFalse(sha256(refreshTokenRaw))
                .ifPresent(t -> t.setRevoked(true));
    }

    public void requestMagicLink(String email) {
        // Stub: in production, persist token + send email via Brevo/Mailhog
        userRepository.findByEmailIgnoreCase(email).orElse(null);
    }

    // Seule l'empreinte SHA-256 du refresh est enregistrée ; le client reçoit la valeur brute une fois.
    private TokenResponse issueTokens(User user) {
        String access = jwtService.createAccessToken(user.getId(), user.getEmail(), user.getRoles());
        String refreshRaw = UUID.randomUUID() + "." + UUID.randomUUID();
        RefreshToken refresh = new RefreshToken();
        refresh.setUserId(user.getId());
        refresh.setTokenHash(sha256(refreshRaw));
        refresh.setExpiresAt(Instant.now().plusSeconds(jwtService.getRefreshTokenDays() * 86400));
        refreshTokenRepository.save(refresh);
        Set<String> roles = user.getRoles().stream().map(Enum::name).collect(Collectors.toSet());
        return new TokenResponse(access, refreshRaw, "Bearer", 15, roles);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
