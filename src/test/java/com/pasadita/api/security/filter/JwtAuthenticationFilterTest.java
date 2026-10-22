package com.pasadita.api.security.filter;

import com.pasadita.api.entities.Employee;
import com.pasadita.api.repositories.EmployeeRepository;
import com.pasadita.api.security.TokenJwtConfig;
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
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    private static final SecretKey SECRET_KEY = Keys.hmacShaKeyFor(new byte[32]);

    @Mock private AuthenticationManager authenticationManager;
    @Mock private TokenJwtConfig tokenJwtConfig;
    @Mock private EmployeeRepository employeeRepository;

    private JsonMapper jsonMapper;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        jsonMapper = bootJsonMapper();
        filter = new JwtAuthenticationFilter(authenticationManager, tokenJwtConfig, employeeRepository, jsonMapper);
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

    @Test
    void successfulLoginExposesOnlyRolesNotSecurityFactors() throws Exception {
        when(tokenJwtConfig.getExpirationDate()).thenReturn(new Date(System.currentTimeMillis() + 60_000));
        when(tokenJwtConfig.getSecretKey()).thenReturn(SECRET_KEY);
        when(employeeRepository.findByUsername("admin"))
                .thenReturn(Optional.of(Employee.builder().id(1L).username("admin").build()));
        Authentication authResult = UsernamePasswordAuthenticationToken.authenticated("admin", null, List.of(
                new SimpleGrantedAuthority("ROLE_ADMIN"),
                FactorGrantedAuthority.fromAuthority(FactorGrantedAuthority.PASSWORD_AUTHORITY)));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.successfulAuthentication(new MockHttpServletRequest(), response, null, authResult);

        JsonNode body = jsonMapper.readTree(response.getContentAsString());
        assertThat(body.get("authorities").toString()).isEqualTo("[{\"authority\":\"ROLE_ADMIN\"}]");
        assertThat(body.get("employeeId").asLong()).isEqualTo(1L);
        Claims claims = Jwts.parser().verifyWith(SECRET_KEY).build()
                .parseSignedClaims(body.get("token").asString()).getPayload();
        assertThat(claims.get("authorities", List.class)).containsExactly(Map.of("authority", "ROLE_ADMIN"));
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
