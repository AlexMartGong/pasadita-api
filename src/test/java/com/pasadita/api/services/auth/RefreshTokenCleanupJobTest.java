package com.pasadita.api.services.auth;

import com.pasadita.api.repositories.RefreshTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RefreshTokenCleanupJobTest {

    @Mock private RefreshTokenRepository refreshTokenRepository;

    @Test
    void purgesTokensExpiredOrRevokedMoreThanThirtyDaysAgo() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-21T10:00:00Z"), ZoneOffset.UTC);

        new RefreshTokenCleanupJob(refreshTokenRepository, clock).purgeStaleTokens();

        verify(refreshTokenRepository).deleteExpiredOrRevokedBefore(LocalDateTime.of(2026, 10, 22, 10, 0));
    }
}
