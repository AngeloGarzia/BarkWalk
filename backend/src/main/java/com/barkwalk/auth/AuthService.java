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
import java.util.Optional;
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
        profile.setReviewStatus("VALIDATED");
        profileRepository.save(profile);
        return issueTokens(user);
    }

    private static final int MAX_FAILED_LOGINS = 5;
    private static final long LOCK_MINUTES = 15;

    @Transactional
    public TokenResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase();
        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (user != null
                && user.getLockedUntil() != null
                && user.getLockedUntil().isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.LOCKED, "Account temporarily locked");
        }
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, request.password()));
        } catch (Exception ex) {
            if (user != null) {
                int attempts = user.getFailedLoginAttempts() + 1;
                user.setFailedLoginAttempts(attempts);
                if (attempts >= MAX_FAILED_LOGINS) {
                    user.setLockedUntil(Instant.now().plusSeconds(LOCK_MINUTES * 60));
                    user.setFailedLoginAttempts(0);
                }
                user.setUpdatedAt(Instant.now());
                userRepository.save(user);
            }
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        User ok = userRepository
                .findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        ok.setFailedLoginAttempts(0);
        ok.setLockedUntil(null);
        ok.setUpdatedAt(Instant.now());
        userRepository.save(ok);
        return issueTokens(ok);
    }

    // Rotation avec famille : réutilisation d'un refresh révoqué → révocation de toute la famille.
    @Transactional
    public TokenResponse refresh(String refreshTokenRaw) {
        String hash = sha256(refreshTokenRaw);
        Optional<RefreshToken> maybe = refreshTokenRepository.findByTokenHash(hash);
        if (maybe.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
        }
        RefreshToken stored = maybe.get();
        if (stored.isRevoked()) {
            stored.setReusedAt(Instant.now());
            refreshTokenRepository.save(stored);
            for (RefreshToken sibling : refreshTokenRepository.findByFamilyId(stored.getFamilyId())) {
                sibling.setRevoked(true);
                refreshTokenRepository.save(sibling);
            }
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token reuse detected");
        }
        if (stored.getExpiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token expired");
        }
        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User missing"));
        stored.setRevoked(true);
        refreshTokenRepository.save(stored);
        TokenResponse tokens = issueTokens(user, stored.getFamilyId());
        refreshTokenRepository.findByTokenHash(sha256(tokens.refreshToken())).ifPresent(created -> {
            stored.setReplacedByTokenId(created.getId());
            refreshTokenRepository.save(stored);
        });
        return tokens;
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
        return issueTokens(user, UUID.randomUUID());
    }

    private TokenResponse issueTokens(User user, UUID familyId) {
        String access = jwtService.createAccessToken(user.getId(), user.getEmail(), user.getRoles());
        String refreshRaw = UUID.randomUUID() + "." + UUID.randomUUID();
        RefreshToken refresh = new RefreshToken();
        refresh.setUserId(user.getId());
        refresh.setTokenHash(sha256(refreshRaw));
        refresh.setFamilyId(familyId != null ? familyId : UUID.randomUUID());
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
