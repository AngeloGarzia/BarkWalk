package com.barkwalk.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.barkwalk.support.SecurityTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class SecurityUtilsTest {

    @AfterEach
    void clear() {
        SecurityTestSupport.clear();
    }

    // Retourne l'utilisateur courant authentifié.
    @Test
    void currentUser_whenAuthenticated() {
        SecurityTestSupport.authenticate(4L, "a@b.c", Role.USER);
        assertThat(SecurityUtils.currentUserId()).isEqualTo(4L);
        assertThat(SecurityUtils.currentUser().getUsername()).isEqualTo("a@b.c");
    }

    // Lève une erreur si utilisateur anonyme.
    @Test
    void currentUser_whenAnonymous() {
        assertThatThrownBy(SecurityUtils::currentUser)
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
