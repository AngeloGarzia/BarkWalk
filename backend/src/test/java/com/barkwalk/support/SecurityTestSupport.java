package com.barkwalk.support;

import com.barkwalk.auth.BarkWalkUserPrincipal;
import com.barkwalk.common.Role;
import com.barkwalk.user.domain.User;
import java.util.Arrays;
import java.util.HashSet;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityTestSupport {
    private SecurityTestSupport() {}

    public static void authenticate(long userId, String email, Role... roles) {
        User user = new User();
        // id via reflection-free path: principal needs User; set fields used by principal
        try {
            var idField = User.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(user, userId);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        user.setEmail(email);
        user.setPasswordHash("n/a");
        user.setEnabled(true);
        user.setDeleted(false);
        user.setRoles(new HashSet<>(Arrays.asList(roles)));
        BarkWalkUserPrincipal principal = new BarkWalkUserPrincipal(user);
        var auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    public static void clear() {
        SecurityContextHolder.clearContext();
    }
}
