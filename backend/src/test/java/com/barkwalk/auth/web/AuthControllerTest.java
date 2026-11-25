package com.barkwalk.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.barkwalk.auth.AuthService;
import com.barkwalk.auth.dto.AuthDtos.LoginRequest;
import com.barkwalk.auth.dto.AuthDtos.MagicLinkRequest;
import com.barkwalk.auth.dto.AuthDtos.RefreshRequest;
import com.barkwalk.auth.dto.AuthDtos.RegisterRequest;
import com.barkwalk.auth.dto.AuthDtos.TokenResponse;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    @Test
    void delegatesToAuthService() {
        TokenResponse tokens = new TokenResponse("a", "r", "Bearer", 15, Set.of("USER"));
        when(authService.register(any())).thenReturn(tokens);
        when(authService.login(any())).thenReturn(tokens);
        when(authService.refresh(any())).thenReturn(tokens);

        assertThat(authController.register(
                        new RegisterRequest("a@b.c", "Secret1!", "A", "B", 1L, true)))
                .isEqualTo(tokens);
        assertThat(authController.login(new LoginRequest("a@b.c", "Secret1!"))).isEqualTo(tokens);
        assertThat(authController.refresh(new RefreshRequest("raw"))).isEqualTo(tokens);
        authController.logout(new RefreshRequest("raw"));
        authController.magicLink(new MagicLinkRequest("a@b.c"));
        verify(authService).logout("raw");
        verify(authService).requestMagicLink("a@b.c");
    }
}
