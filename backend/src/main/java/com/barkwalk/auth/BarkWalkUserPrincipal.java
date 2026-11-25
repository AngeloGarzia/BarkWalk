package com.barkwalk.auth;

import com.barkwalk.common.Role;
import com.barkwalk.user.domain.User;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
// Principal sécurité rattaché à la requête (identifiant, e-mail, rôles).

public class BarkWalkUserPrincipal implements UserDetails {
    private final Long id;
    private final String email;
    private final String passwordHash;
    private final boolean enabled;
    private final Set<Role> roles;

    public BarkWalkUserPrincipal(User user) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.passwordHash = user.getPasswordHash();
        this.enabled = user.isEnabled() && !user.isDeleted();
        this.roles = user.getRoles();
    }

    public Long getId() { return id; }
    public Set<Role> getRoles() { return roles; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r.name())).collect(Collectors.toSet());
    }

    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return email; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return enabled; }
}
