package com.pasadita.api.services.auth;

import com.pasadita.api.config.AuthProperties;
import com.pasadita.api.dto.auth.AuthMapper;
import com.pasadita.api.dto.auth.AuthSession;
import com.pasadita.api.entities.Employee;
import com.pasadita.api.entities.RefreshToken;
import com.pasadita.api.enums.user.Position;
import com.pasadita.api.exceptions.EmployeeInactiveException;
import com.pasadita.api.exceptions.InvalidRefreshTokenException;
import com.pasadita.api.exceptions.RefreshTokenReusedException;
import com.pasadita.api.repositories.EmployeeRepository;
import com.pasadita.api.repositories.RefreshTokenRepository;
import com.pasadita.api.security.JwtTokenProvider;
import com.pasadita.api.security.TokenJwtConfig;
import com.pasadita.api.utils.OpaqueTokenUtils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Runs over an in-memory token table: every repository lookup reads the rows the service saved, so a rotation, a
 * sibling and a family revocation are visible to the next call, as they would be after a commit.
 */
@ExtendWith(MockitoExtension.class)
class AuthSessionServiceImplTest {

    private static final SecretKey SECRET_KEY = Keys.hmacShaKeyFor(new byte[32]);
    private static final Instant START = Instant.parse("2026-11-21T18:00:00Z");
    private static final String FAMILY = "0d6f3c56-6a43-4f43-9f35-6b7c1d2e3f40";
    private static final String OTHER_FAMILY = "9a1b2c3d-4e5f-4a6b-8c7d-0e1f2a3b4c5d";
    private static final String BROWSER = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/142.0";
    private static final String PHONE = "Mozilla/5.0 (Linux; Android 15) Chrome/142.0 Mobile";

    @Mock private EmployeeRepository employeeRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private TokenJwtConfig tokenJwtConfig;

    private final Map<String, RefreshToken> tokensByHash = new LinkedHashMap<>();
    private long nextId = 100;
    private MutableClock clock;
    private Employee employee;
    private AuthSessionServiceImpl service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(START);
        employee = Employee.builder().id(1L).username("admin").position(Position.ROLE_ADMIN).active(true).build();
        lenient().when(tokenJwtConfig.getSecretKey()).thenReturn(SECRET_KEY);
        lenient().when(tokenJwtConfig.getExpirationDate())
                .thenAnswer(invocation -> new Date(System.currentTimeMillis() + 3_600_000));
        lenient().when(employeeRepository.findByUsername("admin")).thenReturn(Optional.of(employee));
        lenient().when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> {
            RefreshToken token = invocation.getArgument(0);
            if (token.getId() == null) {
                token.setId(nextId++);
            }
            tokensByHash.put(token.getTokenHash(), token);
            return token;
        });
        lenient().when(refreshTokenRepository.findByTokenHashForUpdate(anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(tokensByHash.get(invocation.<String>getArgument(0))));
        lenient().when(refreshTokenRepository.findById(any())).thenAnswer(invocation -> tokensByHash.values().stream()
                .filter(token -> token.getId().equals(invocation.getArgument(0)))
                .findFirst());
        lenient().when(refreshTokenRepository.existsByFamilyIdAndRevokedAtIsNullAndExpiresAtAfter(anyString(), any()))
                .thenAnswer(invocation -> tokensByHash.values().stream().anyMatch(token ->
                        token.getFamilyId().equals(invocation.getArgument(0))
                                && token.getRevokedAt() == null
                                && token.getExpiresAt().isAfter(invocation.getArgument(1))));
        lenient().when(refreshTokenRepository.revokeFamily(anyString(), any())).thenAnswer(invocation -> {
            int revoked = 0;
            for (RefreshToken token : tokensByHash.values()) {
                if (token.getFamilyId().equals(invocation.getArgument(0)) && token.getRevokedAt() == null) {
                    token.setRevokedAt(invocation.getArgument(1));
                    revoked++;
                }
            }
            return revoked;
        });
        service = new AuthSessionServiceImpl(employeeRepository, refreshTokenRepository,
                new JwtTokenProvider(tokenJwtConfig), new AuthMapper(), new AuthProperties(60, true), clock);
    }

    // --- start (login) ---

    @Test
    void loginStoresOnlyTheHashOfTheFirstTokenOfANewFamily() {
        AuthSession session = service.start("admin", "Mozilla/5.0");

        assertThat(tokensByHash).hasSize(1);
        RefreshToken stored = tokensByHash.values().iterator().next();
        assertThat(session.refreshToken()).isNotBlank();
        assertThat(stored.getTokenHash())
                .isNotEqualTo(session.refreshToken())
                .matches("[0-9a-f]{64}")
                .isEqualTo(OpaqueTokenUtils.sha256Hex(session.refreshToken()));
        assertThat(stored.getFamilyId()).matches("[0-9a-f-]{36}");
        assertThat(stored.getEmployee()).isSameAs(employee);
        assertThat(stored.getCreatedAt()).isEqualTo(now());
        assertThat(stored.getExpiresAt()).isEqualTo(now().plusDays(60));
        assertThat(stored.getRevokedAt()).isNull();
        assertThat(stored.getUserAgent()).isEqualTo("Mozilla/5.0");
        assertThat(session.body().username()).isEqualTo("admin");
        assertThat(session.body().employeeId()).isEqualTo(1L);
        assertThat(session.body().message()).isEqualTo("Login successful");
        assertThat(claims(session).getSubject()).isEqualTo("admin");
        assertThat(claims(session).get("authorities", List.class)).containsExactly(Map.of("authority", "ROLE_ADMIN"));
    }

    @Test
    void eachLoginOpensItsOwnFamily() {
        service.start("admin", null);
        service.start("admin", null);

        assertThat(tokensByHash.values()).extracting(RefreshToken::getFamilyId).doesNotHaveDuplicates();
    }

    @Test
    void longUserAgentIsTruncatedToTheColumnLength() {
        service.start("admin", "x".repeat(300));

        assertThat(tokensByHash.values().iterator().next().getUserAgent()).hasSize(255);
    }

    // --- rotation ---

    @Test
    void refreshRotatesWithinTheFamilyAndSlidesTheExpiry() {
        String presented = seedToken(FAMILY, now().minusDays(10), now().plusDays(50));
        RefreshToken old = stored(presented);

        AuthSession session = service.refresh(presented, "Mozilla/5.0");

        RefreshToken successor = stored(session.refreshToken());
        assertThat(session.refreshToken()).isNotEqualTo(presented);
        assertThat(successor.getFamilyId()).isEqualTo(FAMILY);
        assertThat(successor.getExpiresAt()).isEqualTo(now().plusDays(60));
        assertThat(successor.getRevokedAt()).isNull();
        assertThat(old.getRevokedAt()).isEqualTo(now());
        assertThat(old.getReplacedById()).isEqualTo(successor.getId());
        assertThat(session.body().message()).isEqualTo("Token refreshed");
        assertThat(session.body().employeeId()).isEqualTo(1L);
    }

    @Test
    void refreshTakesTheEmployeesCurrentRoleAndUsername() {
        String presented = seedToken(FAMILY, now().minusDays(1), now().plusDays(59));
        employee.setPosition(Position.ROLE_CAJERO);
        employee.setUsername("cajero1");

        AuthSession session = service.refresh(presented, null);

        assertThat(claims(session).getSubject()).isEqualTo("cajero1");
        assertThat(claims(session).get("authorities", List.class)).containsExactly(Map.of("authority", "ROLE_CAJERO"));
        assertThat(session.body().authorities()).extracting("authority").containsExactly("ROLE_CAJERO");
    }

    @Test
    void missingOrUnknownCookieIsAnInvalidRefreshToken() {
        assertInvalid(null);
        assertInvalid("");
        assertInvalid("   ");
        assertInvalid("not-a-token-we-issued");
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void expiredTokenIsInvalidAndNothingIsWritten() {
        String presented = seedToken(FAMILY, now().minusDays(60), now());

        assertInvalid(presented);
        assertThat(stored(presented).getRevokedAt()).isNull();
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void tokenRevokedByLogoutOrByAnAdminIsInvalidWithoutAReuseAlarm() {
        String presented = seedToken(FAMILY, now().minusDays(1), now().plusDays(59));
        String sibling = seedToken(FAMILY, now().minusDays(1), now().plusDays(59));
        stored(presented).setRevokedAt(now().minusSeconds(5));

        assertInvalid(presented);
        verify(refreshTokenRepository, never()).revokeFamily(anyString(), any());
        assertThat(stored(sibling).getRevokedAt()).isNull();
    }

    // --- reuse ---

    @Test
    void rotatedTokenReplayedAfterTheGraceRevokesTheWholeFamily() {
        String presented = seedToken(FAMILY, now().minusDays(1), now().plusDays(59));
        AuthSession rotated = service.refresh(presented, null);
        String otherDevice = seedToken(OTHER_FAMILY, now().minusDays(1), now().plusDays(59));
        Duration justPastGrace = AuthSessionServiceImpl.ROTATION_GRACE.plusSeconds(1);
        clock.advance(justPastGrace);

        assertThatThrownBy(() -> service.refresh(presented, null))
                .isInstanceOf(RefreshTokenReusedException.class)
                .extracting("code").isEqualTo("refresh_token_reused");

        verify(refreshTokenRepository).revokeFamily(FAMILY, now());
        assertThat(stored(rotated.refreshToken()).getRevokedAt()).isEqualTo(now());
        // The original rotation time is kept (the update only touches active rows)
        assertThat(stored(presented).getRevokedAt()).isEqualTo(now().minus(justPastGrace));
        assertThat(stored(otherDevice).getRevokedAt()).isNull();
    }

    // The 401 is thrown after revoking: without noRollbackFor the revocation would be rolled back with it, and
    // without READ_COMMITTED a request that waited on the lock would not see the rotation committed before it
    @Test
    void refreshCommitsTheFamilyRevocationDespiteThe401() throws NoSuchMethodException {
        Transactional transactional = AuthSessionServiceImpl.class
                .getMethod("refresh", String.class, String.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional.isolation()).isEqualTo(Isolation.READ_COMMITTED);
        assertThat(transactional.noRollbackFor()).contains(RefreshTokenReusedException.class);
    }

    // --- grace window ---

    @Test
    void twoTabsRefreshingAtOnceBothStayLoggedIn() {
        String presented = seedToken(FAMILY, now().minusDays(1), now().plusDays(59));
        AuthSession firstTab = service.refresh(presented, BROWSER);
        RefreshToken firstSuccessor = stored(firstTab.refreshToken());
        LocalDateTime rotatedAt = now();
        clock.advance(Duration.ofSeconds(5));

        AuthSession secondTab = service.refresh(presented, BROWSER);

        RefreshToken sibling = stored(secondTab.refreshToken());
        assertThat(secondTab.refreshToken()).isNotEqualTo(firstTab.refreshToken());
        assertThat(sibling.getFamilyId()).isEqualTo(FAMILY);
        assertThat(sibling.getRevokedAt()).isNull();
        assertThat(firstSuccessor.getRevokedAt()).isNull();
        // The rotated token keeps its first successor and its rotation time: the grace is never extended
        assertThat(stored(presented).getReplacedById()).isEqualTo(firstSuccessor.getId());
        assertThat(stored(presented).getRevokedAt()).isEqualTo(rotatedAt);
        verify(refreshTokenRepository, never()).revokeFamily(anyString(), any());
    }

    // The phone never received the rotation's answer, so its cookie still holds the old token: the retry must hand it
    // a new refresh token, or its next refresh (well after the grace) would be taken as reuse and log it out
    @Test
    void phoneRetryAfterALostResponseKeepsTheSessionAlive() {
        String presented = seedToken(FAMILY, now().minusDays(1), now().plusDays(59));
        service.refresh(presented, PHONE); // answer lost on the way
        clock.advance(Duration.ofSeconds(2));

        AuthSession retry = service.refresh(presented, PHONE);
        clock.advance(Duration.ofMinutes(60));
        AuthSession nextHour = service.refresh(retry.refreshToken(), PHONE);

        assertThat(nextHour.body().token()).isNotBlank();
        assertThat(stored(retry.refreshToken()).getReplacedById()).isEqualTo(stored(nextHour.refreshToken()).getId());
        verify(refreshTokenRepository, never()).revokeFamily(anyString(), any());
    }

    // Inside the window but from another client than the one that rotated: a stolen copy, not a tab or a retry
    @Test
    void replayInsideTheGraceFromAnotherClientIsTakenAsReuse() {
        String presented = seedToken(FAMILY, now().minusDays(1), now().plusDays(59));
        AuthSession rotated = service.refresh(presented, BROWSER);
        clock.advance(Duration.ofSeconds(5));
        int rowsBefore = tokensByHash.size();

        assertThatThrownBy(() -> service.refresh(presented, "curl/8.9.1"))
                .isInstanceOf(RefreshTokenReusedException.class);

        assertThat(tokensByHash).hasSize(rowsBefore);
        assertThat(stored(rotated.refreshToken()).getRevokedAt()).isEqualTo(now());
    }

    @Test
    void graceNeverResumesASessionThatAlreadyEnded() {
        String presented = seedToken(FAMILY, now().minusDays(1), now().plusDays(59));
        AuthSession rotated = service.refresh(presented, null);
        service.logout(rotated.refreshToken());
        clock.advance(Duration.ofSeconds(5));
        int rowsBefore = tokensByHash.size();

        assertThatThrownBy(() -> service.refresh(presented, null))
                .isExactlyInstanceOf(InvalidRefreshTokenException.class);
        assertThat(tokensByHash).hasSize(rowsBefore);
    }

    // --- inactive employee ---

    @Test
    void deactivatedEmployeeCannotRefreshAndNothingIsWritten() {
        String presented = seedToken(FAMILY, now().minusDays(1), now().plusDays(59));
        employee.setActive(false);

        assertThatThrownBy(() -> service.refresh(presented, null))
                .isInstanceOf(EmployeeInactiveException.class)
                .hasMessage("Usuario inactivo");
        assertThat(stored(presented).getRevokedAt()).isNull();
        assertThat(tokensByHash).hasSize(1);
    }

    @Test
    void deactivatedEmployeeGetsNoSiblingInsideTheGraceEither() {
        String presented = seedToken(FAMILY, now().minusDays(1), now().plusDays(59));
        service.refresh(presented, null);
        employee.setActive(false);
        clock.advance(Duration.ofSeconds(5));
        int rowsBefore = tokensByHash.size();

        assertThatThrownBy(() -> service.refresh(presented, null)).isInstanceOf(EmployeeInactiveException.class);
        assertThat(tokensByHash).hasSize(rowsBefore);
    }

    // --- logout and revocation ---

    @Test
    void logoutEndsTheWholeSessionOfThatDevice() {
        String presented = seedToken(FAMILY, now().minusDays(1), now().plusDays(59));
        String orphanSibling = seedToken(FAMILY, now().minusDays(1), now().plusDays(59));
        String otherDevice = seedToken(OTHER_FAMILY, now().minusDays(1), now().plusDays(59));

        service.logout(presented);

        assertThat(stored(presented).getRevokedAt()).isEqualTo(now());
        assertThat(stored(orphanSibling).getRevokedAt()).isEqualTo(now());
        assertThat(stored(otherDevice).getRevokedAt()).isNull();
        assertInvalid(presented);
    }

    @Test
    void logoutWithoutACookieTouchesNothing() {
        service.logout(null);
        service.logout(" ");

        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    void logoutWithAnUnknownCookieRevokesNothing() {
        service.logout("not-a-token-we-issued");

        verify(refreshTokenRepository, never()).revokeFamily(anyString(), any());
    }

    @Test
    void revokeAllSessionsRevokesEveryActiveTokenOfTheEmployee() {
        service.revokeAllSessions(1L);

        verify(refreshTokenRepository).revokeAllActiveByEmployeeId(1L, now());
    }

    // --- helpers ---

    private void assertInvalid(String refreshToken) {
        assertThatThrownBy(() -> service.refresh(refreshToken, null))
                .isExactlyInstanceOf(InvalidRefreshTokenException.class)
                .extracting("code").isEqualTo("invalid_refresh_token");
    }

    private String seedToken(String familyId, LocalDateTime createdAt, LocalDateTime expiresAt) {
        String rawValue = OpaqueTokenUtils.newToken();
        RefreshToken token = RefreshToken.builder()
                .id(nextId++)
                .employee(employee)
                .tokenHash(OpaqueTokenUtils.sha256Hex(rawValue))
                .familyId(familyId)
                .createdAt(createdAt)
                .expiresAt(expiresAt)
                .build();
        tokensByHash.put(token.getTokenHash(), token);
        return rawValue;
    }

    private RefreshToken stored(String rawValue) {
        return tokensByHash.get(OpaqueTokenUtils.sha256Hex(rawValue));
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private static Claims claims(AuthSession session) {
        return Jwts.parser().verifyWith(SECRET_KEY).build().parseSignedClaims(session.body().token()).getPayload();
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
