package com.barkwalk.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import com.barkwalk.common.Role;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecret("barkwalk-test-secret-key-32chars-min!!");
        props.setAccessTokenMinutes(15);
        props.setRefreshTokenDays(14);
        jwtService = new JwtService(props);
    }

    // Crée et analyse un jeton avec succès.
    @Test
    void createAndParse_roundTrip() {
        String token = jwtService.createAccessToken(9L, "a@b.c", Set.of(Role.USER, Role.MEMBER));
        var claims = jwtService.parse(token);
        assertThat(claims.getSubject()).isEqualTo("9");
        assertThat(claims.get("email", String.class)).isEqualTo("a@b.c");
        assertThat(jwtService.getRefreshTokenDays()).isEqualTo(14L);
    }
}
