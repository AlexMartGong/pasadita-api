package com.pasadita.api.security.filter;

import com.pasadita.api.config.AuthProperties;
import com.pasadita.api.dto.auth.AuthMapper;
import com.pasadita.api.entities.Employee;
import com.pasadita.api.entities.RefreshToken;
import com.pasadita.api.enums.user.Position;
import com.pasadita.api.repositories.EmployeeRepository;
import com.pasadita.api.repositories.RefreshTokenRepository;
import com.pasadita.api.security.JwtTokenProvider;
import com.pasadita.api.security.RefreshTokenCookieFactory;
import com.pasadita.api.security.TokenJwtConfig;
import com.pasadita.api.services.auth.AuthSessionServiceImpl;
import com.pasadita.api.utils.OpaqueTokenUtils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    private static final SecretKey SECRET_KEY = Keys.hmacShaKeyFor(new byte[32]);

    @Mock private AuthenticationManager authenticationManager;
    @Mock private TokenJwtConfig tokenJwtConfig;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;

    private JsonMapper jsonMapper;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        jsonMapper = bootJsonMapper();
        AuthProperties authProperties = new AuthProperties(60, true);
        // The real session service and cookie factory: what the frontend receives is built end to end
        AuthSessionServiceImpl authSessionService = new AuthSessionServiceImpl(employeeRepository,
                refreshTokenRepository, new JwtTokenProvider(tokenJwtConfig), new AuthMapper(), authProperties,
                Clock.fixed(Instant.parse("2026-11-21T18:00:00Z"), ZoneOffset.UTC));
        filter = new JwtAuthenticationFilter(authenticationManager, authSessionService,
                new RefreshTokenCookieFactory(authProperties), jsonMapper);
    }

    @Test
    void loginBodyIsAuthenticatedWithItsCredentials() {
        filter.attemptAuthentication(loginRequest("{\"username\":\"admin\",\"password\":\"123456\"}"),
                new MockHttpServletResponse());

        ArgumentCaptor<Authentication> captor = ArgumentCaptor.forClass(Authentication.class);
        verify(authenticationManager).authenticate(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("admin");
        assertThat(captor.getValue().getCredentials()).isEqualTo("123456");
    }

    @Test
    void malformedLoginBodyStaysAnAuthenticationFailure() {
        assertThatThrownBy(() -> filter.attemptAuthentication(loginRequest("{\"username\":"),
                new MockHttpServletResponse()))
                .isInstanceOf(AuthenticationServiceException.class);
    }

    // The frontend's contract: the same six keys as before refresh tokens existed, plus the cookie on the side
    @Test
    @SuppressWarnings("unchecked")
    void successfulLoginKeepsTheSameBodyAndAddsTheRefreshCookie() throws Exception {
        Date expirationDate = new Date(System.currentTimeMillis() + 60_000);
        when(tokenJwtConfig.getExpirationDate()).thenReturn(expirationDate);
        when(tokenJwtConfig.getSecretKey()).thenReturn(SECRET_KEY);
        when(employeeRepository.findByUsername("admin")).thenReturn(Optional.of(
                Employee.builder().id(1L).username("admin").position(Position.ROLE_ADMIN).active(true).build()));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Authentication authResult = UsernamePasswordAuthenticationToken.authenticated("admin", null, List.of(
                new SimpleGrantedAuthority("ROLE_ADMIN"),
                FactorGrantedAuthority.fromAuthority(FactorGrantedAuthority.PASSWORD_AUTHORITY)));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
        request.addHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.successfulAuthentication(request, response, null, authResult);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).startsWith("application/json");
        String content = response.getContentAsString();
        assertThat(jsonMapper.readValue(content, Map.class).keySet())
                .containsExactlyInAnyOrder("token", "username", "employeeId", "authorities", "message", "expiresAt");
        JsonNode body = jsonMapper.readTree(content);
        assertThat(body.get("username").asString()).isEqualTo("admin");
        assertThat(body.get("employeeId").asLong()).isEqualTo(1L);
        assertThat(body.get("authorities").toString()).isEqualTo("[{\"authority\":\"ROLE_ADMIN\"}]");
        assertThat(body.get("message").asString()).isEqualTo("Login successful");
        assertThat(body.get("expiresAt").asLong()).isEqualTo(expirationDate.getTime());
        String token = body.get("token").asString();
        assertThat(response.getHeader(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer " + token);

        Claims claims = Jwts.parser().verifyWith(SECRET_KEY).build().parseSignedClaims(token).getPayload();
        assertThat(claims.getSubject()).isEqualTo("admin");
        assertThat(claims.get("authorities", List.class)).containsExactly(Map.of("authority", "ROLE_ADMIN"));
        assertThat(claims.getIssuedAt()).isNotNull();
        assertThat(claims.getExpiration().getTime() / 1000).isEqualTo(expirationDate.getTime() / 1000);

        String cookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(cookie)
                .startsWith("refresh_token=")
                .contains("Path=/api/auth", "Max-Age=5184000", "HttpOnly", "Secure", "SameSite=Lax")
                .doesNotContain("Domain");
        String rawRefreshToken = cookie.substring("refresh_token=".length(), cookie.indexOf(';'));
        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(saved.capture());
        assertThat(saved.getValue().getTokenHash()).isEqualTo(OpaqueTokenUtils.sha256Hex(rawRefreshToken));
        assertThat(saved.getValue().getUserAgent()).isEqualTo("Mozilla/5.0");
    }

    private static MockHttpServletRequest loginRequest(String body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
        request.setContentType("application/json");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }

    // The JsonMapper Spring Boot auto-configures from the real application.properties,
    // i.e. the one SpringSecurityConfig hands the filter
    private static JsonMapper bootJsonMapper() {
        AtomicReference<JsonMapper> mapper = new AtomicReference<>();
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
                .run(context -> mapper.set(context.getBean(JsonMapper.class)));
        return mapper.get();
    }
}
