package com.pasadita.api.security.filter;

import com.pasadita.api.config.AuthProperties;
import com.pasadita.api.dto.auth.AuthMapper;
import com.pasadita.api.dto.auth.AuthSession;
import com.pasadita.api.entities.Employee;
import com.pasadita.api.entities.RefreshToken;
import com.pasadita.api.enums.user.Position;
import com.pasadita.api.repositories.EmployeeRepository;
import com.pasadita.api.repositories.RefreshTokenRepository;
import com.pasadita.api.security.JwtTokenProvider;
import com.pasadita.api.security.TokenJwtConfig;
import com.pasadita.api.services.auth.AuthSessionServiceImpl;
import com.pasadita.api.utils.OpaqueTokenUtils;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtValidationFilterTest {

    private static final SecretKey SECRET_KEY = Keys.hmacShaKeyFor(new byte[32]);

    @Mock private AuthenticationManager authenticationManager;
    @Mock private TokenJwtConfig tokenJwtConfig;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // The frontend refreshes precisely when its access token expired, and may still send it
    @Test
    void expiredAccessTokenInTheHeaderDoesNotBlockRefreshOrLogout() throws Exception {
        JwtValidationFilter filter = new JwtValidationFilter(authenticationManager, tokenJwtConfig);

        for (String path : List.of("/api/auth/refresh", "/api/auth/logout")) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
            request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken());
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(request, response, chain);

            assertThat(chain.getRequest()).as(path).isNotNull();
            assertThat(response.getStatus()).as(path).isEqualTo(200);
        }
    }

    @Test
    void expiredAccessTokenIsStillRejectedEverywhereElse() throws Exception {
        when(tokenJwtConfig.getSecretKey()).thenReturn(SECRET_KEY);
        JwtValidationFilter filter = new JwtValidationFilter(authenticationManager, tokenJwtConfig);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/sales/all");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken());
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    // A refreshed token must carry the authorities claim in the shape this filter deserializes
    @Test
    void accessTokenIssuedByARefreshAuthenticatesWithTheEmployeesRole() throws Exception {
        when(tokenJwtConfig.getSecretKey()).thenReturn(SECRET_KEY);
        when(tokenJwtConfig.getExpirationDate()).thenReturn(new Date(System.currentTimeMillis() + 60_000));
        AuthSession refreshed = refreshSessionOf(
                Employee.builder().id(3L).username("pedidos1").position(Position.ROLE_PEDIDOS).active(true).build());
        JwtValidationFilter filter = new JwtValidationFilter(authenticationManager, tokenJwtConfig);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/delivery-orders/all");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + refreshed.body().token());
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Authentication> authenticated = new AtomicReference<>();
        FilterChain chain = (req, res) -> authenticated.set(SecurityContextHolder.getContext().getAuthentication());

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(authenticated.get().getName()).isEqualTo("pedidos1");
        assertThat(authenticated.get().getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_PEDIDOS");
    }

    private AuthSession refreshSessionOf(Employee employee) {
        String presented = OpaqueTokenUtils.newToken();
        LocalDateTime now = LocalDateTime.of(2026, 11, 21, 18, 0);
        RefreshToken stored = RefreshToken.builder().id(1L).employee(employee)
                .tokenHash(OpaqueTokenUtils.sha256Hex(presented)).familyId("f6a1b2c3-d4e5-4f60-8a7b-9c0d1e2f3a4b")
                .createdAt(now.minusDays(1)).expiresAt(now.plusDays(59)).build();
        when(refreshTokenRepository.findByTokenHashForUpdate(OpaqueTokenUtils.sha256Hex(presented)))
                .thenReturn(Optional.of(stored));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> {
            RefreshToken token = invocation.getArgument(0);
            token.setId(2L);
            return token;
        });
        AuthSessionServiceImpl service = new AuthSessionServiceImpl(employeeRepository, refreshTokenRepository,
                new JwtTokenProvider(tokenJwtConfig), new AuthMapper(), new AuthProperties(60, true),
                Clock.fixed(Instant.parse("2026-11-21T18:00:00Z"), ZoneOffset.UTC));
        return service.refresh(presented, null);
    }

    private static String expiredToken() {
        return Jwts.builder()
                .subject("admin")
                .claim("authorities", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .issuedAt(new Date(System.currentTimeMillis() - 120_000))
                .expiration(new Date(System.currentTimeMillis() - 60_000))
                .signWith(SECRET_KEY)
                .compact();
    }
}
