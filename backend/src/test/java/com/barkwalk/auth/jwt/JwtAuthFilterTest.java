package com.barkwalk.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.barkwalk.auth.BarkWalkUserDetailsService;
import com.barkwalk.auth.BarkWalkUserPrincipal;
import com.barkwalk.common.Role;
import com.barkwalk.user.domain.User;
import jakarta.servlet.FilterChain;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock
    private BarkWalkUserDetailsService userDetailsService;
    @Mock
    private FilterChain filterChain;

    private JwtService jwtService;
    private JwtAuthFilter filter;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecret("barkwalk-test-secret-key-32chars-min!!");
        props.setAccessTokenMinutes(15);
        jwtService = new JwtService(props);
        filter = new JwtAuthFilter(jwtService, userDetailsService);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void setsAuthenticationFromBearer() throws Exception {
        String token = jwtService.createAccessToken(1L, "a@b.c", Set.of(Role.USER));
        User user = new User();
        try {
            var f = User.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(user, 1L);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        user.setEmail("a@b.c");
        user.setPasswordHash("x");
        user.setEnabled(true);
        user.setRoles(Set.of(Role.USER));
        when(userDetailsService.loadUserByUsername("a@b.c")).thenReturn(new BarkWalkUserPrincipal(user));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        filter.doFilter(request, new MockHttpServletResponse(), filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        verify(filterChain).doFilter(any(), any());
    }

    @Test
    void ignoresMissingHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        filter.doFilter(request, new MockHttpServletResponse(), filterChain);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(any(), any());
    }
}
