package com.zestindia.productapi.security;

import com.zestindia.productapi.model.AppUser;
import com.zestindia.productapi.model.RefreshToken;
import com.zestindia.productapi.model.Role;
import com.zestindia.productapi.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RefreshTokenService")
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private RefreshTokenService refreshTokenService;

    private final long expiryMs = 604_800_000L; // 7 days, matches app.jwt.refresh-token-expiry-ms

    @BeforeEach
    void setUp() {
        refreshTokenService = new RefreshTokenService(refreshTokenRepository, expiryMs);
    }

    @Test
    @DisplayName("createRefreshToken() persists a fresh opaque, unrevoked, unexpired token")
    void createsFreshToken() {
        AppUser user = new AppUser("admin", "hashed", Role.ROLE_ADMIN);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        RefreshToken created = refreshTokenService.createRefreshToken(user);

        assertThat(created.getToken()).isNotBlank();
        assertThat(created.isRevoked()).isFalse();
        assertThat(created.isExpired()).isFalse();
        assertThat(created.getUser()).isEqualTo(user);
    }

    @Test
    @DisplayName("rotate() revokes the presented token and issues a brand new one")
    void rotateRevokesOldAndIssuesNew() {
        AppUser user = new AppUser("admin", "hashed", Role.ROLE_ADMIN);
        RefreshToken existing = new RefreshToken("old-token", user, Instant.now().plus(1, ChronoUnit.DAYS));

        when(refreshTokenRepository.findByToken("old-token")).thenReturn(Optional.of(existing));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        RefreshToken rotated = refreshTokenService.rotate("old-token");

        assertThat(existing.isRevoked()).isTrue();
        assertThat(rotated.getToken()).isNotEqualTo("old-token");
        assertThat(rotated.isRevoked()).isFalse();

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository, times(2)).save(captor.capture());
    }

    @Test
    @DisplayName("rotate() rejects a token that is not recognized")
    void rotateRejectsUnknownToken() {
        when(refreshTokenRepository.findByToken("ghost-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> refreshTokenService.rotate("ghost-token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not recognized");
    }

    @Test
    @DisplayName("rotate() rejects a token that has already been revoked (replay protection)")
    void rotateRejectsRevokedToken() {
        AppUser user = new AppUser("admin", "hashed", Role.ROLE_ADMIN);
        RefreshToken revoked = new RefreshToken("used-token", user, Instant.now().plus(1, ChronoUnit.DAYS));
        revoked.setRevoked(true);

        when(refreshTokenRepository.findByToken("used-token")).thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> refreshTokenService.rotate("used-token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already been used");

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("rotate() rejects a token that has expired")
    void rotateRejectsExpiredToken() {
        AppUser user = new AppUser("admin", "hashed", Role.ROLE_ADMIN);
        RefreshToken expired = new RefreshToken("stale-token", user, Instant.now().minus(1, ChronoUnit.DAYS));

        when(refreshTokenRepository.findByToken("stale-token")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> refreshTokenService.rotate("stale-token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expired");

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("revoke() marks a known token as revoked")
    void revokeMarksTokenRevoked() {
        AppUser user = new AppUser("admin", "hashed", Role.ROLE_ADMIN);
        RefreshToken token = new RefreshToken("some-token", user, Instant.now().plus(1, ChronoUnit.DAYS));

        when(refreshTokenRepository.findByToken("some-token")).thenReturn(Optional.of(token));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        refreshTokenService.revoke("some-token");

        assertThat(token.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(token);
    }

    @Test
    @DisplayName("revoke() silently no-ops for an unknown token")
    void revokeNoOpsForUnknownToken() {
        when(refreshTokenRepository.findByToken("ghost-token")).thenReturn(Optional.empty());

        refreshTokenService.revoke("ghost-token");

        verify(refreshTokenRepository, never()).save(any());
    }
}
