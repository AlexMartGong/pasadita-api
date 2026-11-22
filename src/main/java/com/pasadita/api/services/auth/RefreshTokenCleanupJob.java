package com.pasadita.api.services.auth;

import com.pasadita.api.repositories.RefreshTokenRepository;
import com.pasadita.api.utils.DateTimeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Daily purge of refresh tokens that expired or were revoked more than {@link #RETENTION} ago. The recent ones stay
 * so a replayed rotated token is still recognized (reuse alarm) and the history can be inspected.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenCleanupJob {

    private static final Duration RETENTION = Duration.ofDays(30);

    private final RefreshTokenRepository refreshTokenRepository;
    private final Clock clock;

    @Scheduled(cron = "0 0 4 * * *", zone = "America/Mexico_City")
    @Transactional
    public void purgeStaleTokens() {
        LocalDateTime cutoff = DateTimeUtils.nowUtc(clock).minus(RETENTION);
        int deleted = refreshTokenRepository.deleteExpiredOrRevokedBefore(cutoff);
        log.info("Limpieza de refresh tokens - eliminados: {}, expirados o revocados antes de {} UTC", deleted, cutoff);
    }
}
