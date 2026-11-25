package com.barkwalk.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.barkwalk.common.Role;
import com.barkwalk.user.domain.User;
import com.barkwalk.user.repository.UserRepository;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@ExtendWith(MockitoExtension.class)
class BarkWalkUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private BarkWalkUserDetailsService service;

    @Test
    void loadsPrincipal() {
        User user = new User();
        try {
            var f = User.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(user, 3L);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        user.setEmail("u@example.com");
        user.setPasswordHash("x");
        user.setEnabled(true);
        user.setRoles(Set.of(Role.USER));
        when(userRepository.findByEmailIgnoreCase("u@example.com")).thenReturn(Optional.of(user));
        assertThat(service.loadUserByUsername("u@example.com").getUsername()).isEqualTo("u@example.com");
    }

    @Test
    void unknownUser() {
        when(userRepository.findByEmailIgnoreCase("x@y.z")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.loadUserByUsername("x@y.z"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
