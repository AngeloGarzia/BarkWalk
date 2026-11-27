package com.barkwalk.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.barkwalk.common.Role;
import com.barkwalk.user.domain.User;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BarkWalkUserPrincipalTest {

    // Expose les getters UserDetails et détecte un compte verrouillé.
    @Test
    void mapsUserDetailsFlags() {
        User user = new User();
        setId(user, 3L);
        user.setEmail("a@example.com");
        user.setPasswordHash("hash");
        user.setEnabled(true);
        user.setDeleted(false);
        user.setRoles(Set.of(Role.USER, Role.MEMBER));
        user.setLockedUntil(Instant.now().plus(5, ChronoUnit.MINUTES));

        BarkWalkUserPrincipal principal = new BarkWalkUserPrincipal(user);
        assertThat(principal.getId()).isEqualTo(3L);
        assertThat(principal.getUsername()).isEqualTo("a@example.com");
        assertThat(principal.getPassword()).isEqualTo("hash");
        assertThat(principal.isEnabled()).isTrue();
        assertThat(principal.isAccountNonExpired()).isTrue();
        assertThat(principal.isCredentialsNonExpired()).isTrue();
        assertThat(principal.isAccountNonLocked()).isFalse();
        assertThat(principal.getAuthorities())
                .extracting(Object::toString)
                .contains("ROLE_USER", "ROLE_MEMBER");
    }

    private static void setId(User user, long id) {
        try {
            var f = User.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(user, id);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
