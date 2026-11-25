package com.barkwalk.auth.jwt;

import com.barkwalk.common.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Set;
import java.util.stream.Collectors;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

// Émission et lecture des jetons d'accès JWT.
@Service
public class JwtService {
    private final JwtProperties properties;
    private final SecretKey key;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(Long userId, String email, Set<Role> roles) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(properties.getAccessTokenMinutes() * 60);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .claim("roles", roles.stream().map(Enum::name).collect(Collectors.toList()))
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public long getRefreshTokenDays() { return properties.getRefreshTokenDays(); }
}
