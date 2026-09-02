package com.zestindia.productapi.security;

import com.zestindia.productapi.model.AppUser;
import com.zestindia.productapi.model.RefreshToken;
import com.zestindia.productapi.repository.RefreshTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

/**
 * Opaque, DB-backed refresh tokens with rotation: every time a refresh
 * token is redeemed for a new access token, the old refresh token is
 * revoked and a brand new one is issued. This limits the blast radius
 * if a refresh token is ever stolen/replayed.
 */
@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final long refreshTokenExpiryMs;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            @Value("${app.jwt.refresh-token-expiry-ms}") long refreshTokenExpiryMs) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenExpiryMs = refreshTokenExpiryMs;
    }

    public RefreshToken createRefreshToken(AppUser user) {
        RefreshToken refreshToken = new RefreshToken(
                generateOpaqueToken(),
                user,
                Instant.now().plus(refreshTokenExpiryMs, ChronoUnit.MILLIS)
        );
        return refreshTokenRepository.save(refreshToken);
    }

    /**
     * Validates the given refresh token and, if valid, revokes it and issues
     * a replacement (rotation). Throws IllegalArgumentException if the token
     * is unknown, revoked, or expired.
     */
    public RefreshToken rotate(String presentedToken) {
        RefreshToken existing = refreshTokenRepository.findByToken(presentedToken)
                .orElseThrow(() -> new IllegalArgumentException("Refresh token not recognized"));

        if (existing.isRevoked()) {
            throw new IllegalArgumentException("Refresh token has already been used/revoked");
        }
        if (existing.isExpired()) {
            throw new IllegalArgumentException("Refresh token has expired");
        }

        existing.setRevoked(true);
        refreshTokenRepository.save(existing);

        return createRefreshToken(existing.getUser());
    }

    public void revoke(String presentedToken) {
        refreshTokenRepository.findByToken(presentedToken).ifPresent(rt -> {
            rt.setRevoked(true);
            refreshTokenRepository.save(rt);
        });
    }

    private String generateOpaqueToken() {
        byte[] bytes = new byte[64];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
