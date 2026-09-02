package com.zestindia.productapi.service;

import com.zestindia.productapi.dto.AuthResponse;
import com.zestindia.productapi.dto.LoginRequest;
import com.zestindia.productapi.dto.RegisterRequest;
import com.zestindia.productapi.model.AppUser;
import com.zestindia.productapi.model.RefreshToken;
import com.zestindia.productapi.model.Role;
import com.zestindia.productapi.repository.UserRepository;
import com.zestindia.productapi.security.JwtService;
import com.zestindia.productapi.security.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService")
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtService jwtService;
    @Mock
    private RefreshTokenService refreshTokenService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, authenticationManager,
                jwtService, refreshTokenService);
    }

    @Test
    @DisplayName("register() creates a USER by default and returns a token pair")
    void registerDefaultsToUserRole() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("newuser");
        request.setPassword("password123");

        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        when(jwtService.generateAccessToken(any())).thenReturn("access-token");
        when(refreshTokenService.createRefreshToken(any()))
                .thenReturn(new RefreshToken("refresh-token", null, Instant.now().plusSeconds(60)));

        AuthResponse response = authService.register(request);

        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token");

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo("newuser");
        assertThat(captor.getValue().getPassword()).isEqualTo("hashed");
        assertThat(captor.getValue().getRole()).isEqualTo(Role.ROLE_USER);
    }

    @Test
    @DisplayName("register() honors an explicit ADMIN role")
    void registerHonorsAdminRole() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("boss");
        request.setPassword("password123");
        request.setRole("ADMIN");

        when(userRepository.existsByUsername("boss")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        when(jwtService.generateAccessToken(any())).thenReturn("access-token");
        when(refreshTokenService.createRefreshToken(any()))
                .thenReturn(new RefreshToken("refresh-token", null, Instant.now().plusSeconds(60)));

        authService.register(request);

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(Role.ROLE_ADMIN);
    }

    @Test
    @DisplayName("register() rejects a username that is already taken")
    void registerRejectsDuplicateUsername() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("admin");
        request.setPassword("password123");

        when(userRepository.existsByUsername("admin")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already taken");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("login() authenticates and returns a fresh token pair")
    void loginReturnsTokens() {
        LoginRequest request = new LoginRequest();
        request.setUsername("admin");
        request.setPassword("admin123");

        AppUser user = new AppUser("admin", "hashed", Role.ROLE_ADMIN);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));
        when(jwtService.generateAccessToken(user)).thenReturn("access-token");
        when(refreshTokenService.createRefreshToken(user))
                .thenReturn(new RefreshToken("refresh-token", user, Instant.now().plusSeconds(60)));

        AuthResponse response = authService.login(request);

        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
    }

    @Test
    @DisplayName("login() propagates BadCredentialsException for a wrong password")
    void loginPropagatesBadCredentials() {
        LoginRequest request = new LoginRequest();
        request.setUsername("admin");
        request.setPassword("wrong");

        doThrow(new BadCredentialsException("Bad credentials"))
                .when(authenticationManager).authenticate(any());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class);

        verify(jwtService, never()).generateAccessToken(any());
    }

    @Test
    @DisplayName("refresh() rotates the refresh token and mints a new access token")
    void refreshRotatesToken() {
        AppUser user = new AppUser("admin", "hashed", Role.ROLE_ADMIN);
        RefreshToken rotated = new RefreshToken("new-refresh-token", user, Instant.now().plusSeconds(60));

        when(refreshTokenService.rotate("old-refresh-token")).thenReturn(rotated);
        when(jwtService.generateAccessToken(user)).thenReturn("new-access-token");

        AuthResponse response = authService.refresh("old-refresh-token");

        assertThat(response.getAccessToken()).isEqualTo("new-access-token");
        assertThat(response.getRefreshToken()).isEqualTo("new-refresh-token");
    }

    @Test
    @DisplayName("logout() delegates revocation to RefreshTokenService")
    void logoutDelegatesRevocation() {
        authService.logout("some-token");

        verify(refreshTokenService).revoke("some-token");
    }
}
