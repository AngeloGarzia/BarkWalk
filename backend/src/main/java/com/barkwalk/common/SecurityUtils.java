package com.barkwalk.common;

import com.barkwalk.auth.BarkWalkUserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
// Accès pratique à l'utilisateur courant depuis le SecurityContext.

public final class SecurityUtils {
    private SecurityUtils() {}

    public static BarkWalkUserPrincipal currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof BarkWalkUserPrincipal principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        return principal;
    }

    public static Long currentUserId() {
        return currentUser().getId();
    }
}
