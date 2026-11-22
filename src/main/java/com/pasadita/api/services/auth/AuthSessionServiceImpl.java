package com.pasadita.api.services.auth;

import com.pasadita.api.config.AuthProperties;
import com.pasadita.api.dto.auth.AuthMapper;
import com.pasadita.api.dto.auth.AuthSession;
import com.pasadita.api.entities.Employee;
import com.pasadita.api.entities.RefreshToken;
import com.pasadita.api.exceptions.EmployeeInactiveException;
import com.pasadita.api.exceptions.EntityNotFoundException;
import com.pasadita.api.exceptions.InvalidRefreshTokenException;
import com.pasadita.api.exceptions.RefreshTokenReusedException;
import com.pasadita.api.repositories.EmployeeRepository;
import com.pasadita.api.repositories.RefreshTokenRepository;
import com.pasadita.api.security.IssuedAccessToken;
import com.pasadita.api.security.JwtTokenProvider;
import com.pasadita.api.utils.DateTimeUtils;
import com.pasadita.api.utils.OpaqueTokenUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthSessionServiceImpl implements AuthSessionService {

    // A token rotated less than this long ago may be presented again (two tabs refreshing at once, or a phone
    // retrying after losing the response): it gets a sibling in the same family instead of a reuse alarm
    static final Duration ROTATION_GRACE = Duration.ofSeconds(30);
    private static final int USER_AGENT_MAX_LENGTH = 255;
    private static final String LOGIN_MESSAGE = "Login successful";
    private static final String REFRESH_MESSAGE = "Token refreshed";
    private static final String INACTIVE_EMPLOYEE_MESSAGE = "Usuario inactivo";

    private final EmployeeRepository employeeRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthMapper authMapper;
    private final AuthProperties authProperties;
    private final Clock clock;

    @Override
    @Transactional
    public AuthSession start(String username, String userAgent) {
        Employee employee = employeeRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("Employee not found with username: " + username));
        LocalDateTime now = DateTimeUtils.nowUtc(clock);
        StoredRefreshToken first = storeNewToken(employee, UUID.randomUUID().toString(), userAgent, now);
        return buildSession(employee, first.rawValue(), LOGIN_MESSAGE);
    }

    // READ_COMMITTED: a request that waited on the row lock must see the rotation the previous holder committed
    // (MySQL's REPEATABLE READ would keep an older snapshot). noRollbackFor: the family revocation done right before
    // throwing RefreshTokenReusedException must be committed, not rolled back with the 401
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED, noRollbackFor = RefreshTokenReusedException.class)
    public AuthSession refresh(String refreshToken, String userAgent) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new InvalidRefreshTokenException();
        }
        LocalDateTime now = DateTimeUtils.nowUtc(clock);
        RefreshToken presented = refreshTokenRepository.findByTokenHashForUpdate(OpaqueTokenUtils.sha256Hex(refreshToken))
                .orElseThrow(InvalidRefreshTokenException::new);

        if (presented.getRevokedAt() != null) {
            return refreshRevoked(presented, userAgent, now);
        }
        if (!presented.getExpiresAt().isAfter(now)) {
            throw new InvalidRefreshTokenException();
        }
        Employee employee = requireActive(presented.getEmployee());

        StoredRefreshToken successor = storeNewToken(employee, presented.getFamilyId(), userAgent, now);
        presented.setRevokedAt(now);
        presented.setReplacedById(successor.entity().getId());
        return buildSession(employee, successor.rawValue(), REFRESH_MESSAGE);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        LocalDateTime now = DateTimeUtils.nowUtc(clock);
        // Same lock as refresh, so a logout never misses the successor of a rotation in flight
        refreshTokenRepository.findByTokenHashForUpdate(OpaqueTokenUtils.sha256Hex(refreshToken))
                .ifPresent(token -> refreshTokenRepository.revokeFamily(token.getFamilyId(), now));
    }

    @Override
    @Transactional
    public void revokeAllSessions(Long employeeId) {
        int revoked = refreshTokenRepository.revokeAllActiveByEmployeeId(employeeId, DateTimeUtils.nowUtc(clock));
        log.info("Sesiones revocadas - EmployeeId: {}, refresh tokens revocados: {}", employeeId, revoked);
    }

    private AuthSession refreshRevoked(RefreshToken presented, String userAgent, LocalDateTime now) {
        // Revoked without a successor = logout or admin revocation (deactivation, password change)
        if (presented.getReplacedById() == null) {
            throw new InvalidRefreshTokenException();
        }
        // The session already ended (logout, revocation, reuse alarm): nothing left to protect or to resume
        if (!refreshTokenRepository.existsByFamilyIdAndRevokedAtIsNullAndExpiresAtAfter(presented.getFamilyId(), now)) {
            throw new InvalidRefreshTokenException();
        }
        boolean withinGrace = !presented.getRevokedAt().isBefore(now.minus(ROTATION_GRACE));
        if (withinGrace && rotatedBySameClient(presented, userAgent)) {
            // A sibling, not just an access token: a client whose rotation response was lost still holds this token
            // and must receive a new one, or its next refresh would trip the reuse alarm
            Employee employee = requireActive(presented.getEmployee());
            StoredRefreshToken sibling = storeNewToken(employee, presented.getFamilyId(), userAgent, now);
            // Audit trail: siblings are the one place a replayed token is honored
            log.info("Refresh dentro de la gracia - EmployeeId: {}, FamilyId: {}, token rotado: {}, hermano: {}",
                    employee.getId(), presented.getFamilyId(), presented.getId(), sibling.entity().getId());
            return buildSession(employee, sibling.rawValue(), REFRESH_MESSAGE);
        }
        int revoked = refreshTokenRepository.revokeFamily(presented.getFamilyId(), now);
        log.warn("Reuso de refresh token detectado - EmployeeId: {}, FamilyId: {}, rotado: {}, dentro de la gracia: {}. "
                        + "Familia revocada ({} tokens)",
                presented.getEmployee().getId(), presented.getFamilyId(), presented.getRevokedAt(), withinGrace, revoked);
        throw new RefreshTokenReusedException();
    }

    // The grace is for the client that just rotated (its other tabs, or its retry after a lost response), so the
    // replay must come with the User-Agent of the successor created seconds ago. Comparing with the successor rather
    // than the presented token keeps a browser update between rotations from looking like another client
    private boolean rotatedBySameClient(RefreshToken rotated, String userAgent) {
        return refreshTokenRepository.findById(rotated.getReplacedById())
                .map(successor -> Objects.equals(successor.getUserAgent(), truncateUserAgent(userAgent)))
                .orElse(false);
    }

    private Employee requireActive(Employee employee) {
        if (!employee.isActive()) {
            throw new EmployeeInactiveException(INACTIVE_EMPLOYEE_MESSAGE);
        }
        return employee;
    }

    private StoredRefreshToken storeNewToken(Employee employee, String familyId, String userAgent, LocalDateTime now) {
        String rawValue = OpaqueTokenUtils.newToken();
        RefreshToken saved = refreshTokenRepository.save(RefreshToken.builder()
                .employee(employee)
                .tokenHash(OpaqueTokenUtils.sha256Hex(rawValue))
                .familyId(familyId)
                .expiresAt(now.plusDays(authProperties.refreshTokenDays()))
                .userAgent(truncateUserAgent(userAgent))
                .createdAt(now)
                .build());
        return new StoredRefreshToken(rawValue, saved);
    }

    // Roles from the employee's current position, like JpaUserDetailsService: a role change applies on the next refresh
    private AuthSession buildSession(Employee employee, String rawRefreshToken, String message) {
        List<GrantedAuthority> roles = List.of(new SimpleGrantedAuthority(employee.getPosition().name()));
        IssuedAccessToken accessToken = jwtTokenProvider.issue(employee.getUsername(), roles);
        return new AuthSession(authMapper.toResponseDto(employee, roles, accessToken, message), rawRefreshToken);
    }

    private static String truncateUserAgent(String userAgent) {
        if (userAgent == null || userAgent.length() <= USER_AGENT_MAX_LENGTH) {
            return userAgent;
        }
        return userAgent.substring(0, USER_AGENT_MAX_LENGTH);
    }

    private record StoredRefreshToken(String rawValue, RefreshToken entity) {

        @Override
        public String toString() {
            return "StoredRefreshToken[id=" + entity.getId() + "]";
        }
    }
}
