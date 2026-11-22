package com.pasadita.api.security.filter;

import com.pasadita.api.dto.auth.AuthSession;
import com.pasadita.api.entities.Employee;
import com.pasadita.api.security.RefreshTokenCookieFactory;
import com.pasadita.api.services.auth.AuthSessionService;
import jakarta.servlet.FilterChain;
import lombok.extern.slf4j.Slf4j;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static com.pasadita.api.security.TokenJwtConfig.*;

@Slf4j
public class JwtAuthenticationFilter extends UsernamePasswordAuthenticationFilter {

    private final AuthenticationManager authenticationManager;
    private final AuthSessionService authSessionService;
    private final RefreshTokenCookieFactory refreshTokenCookieFactory;
    // Spring Boot's mapper, not a raw JsonMapper: Jackson 3's defaults bind through the Lombok
    // all-args constructor, so the absent primitive `active` made every login body fail
    private final JsonMapper jsonMapper;

    public JwtAuthenticationFilter(AuthenticationManager authenticationManager, AuthSessionService authSessionService,
                                   RefreshTokenCookieFactory refreshTokenCookieFactory, JsonMapper jsonMapper) {
        this.authenticationManager = authenticationManager;
        this.authSessionService = authSessionService;
        this.refreshTokenCookieFactory = refreshTokenCookieFactory;
        this.jsonMapper = jsonMapper;
        // POST-only matcher: setFilterProcessesUrl matches every HTTP method,
        // which let CORS preflight OPTIONS reach attemptAuthentication and
        // fail parsing its empty body
        setRequiresAuthenticationRequestMatcher(
                PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/login"));
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) throws AuthenticationException {
        Employee employee;
        String username;
        String password;

        try {
            employee = jsonMapper.readValue(request.getInputStream(), Employee.class);
            username = employee.getUsername();
            password = employee.getPassword();
            // Never log the raw password — length + whitespace flag is enough
            // to spot empty/duplicated/padded credentials coming from the frontend
            log.info("Login attempt: username='{}' (usernameLength={}), passwordLength={}, passwordHasWhitespace={}",
                    username,
                    username != null ? username.length() : -1,
                    password != null ? password.length() : -1,
                    password != null && !password.equals(password.strip()));
        } catch (IOException | JacksonException e) {
            // AuthenticationServiceException keeps a malformed body inside the
            // authentication flow (401 via unsuccessfulAuthentication) instead
            // of escaping the filter chain as a 500
            throw new AuthenticationServiceException("Invalid login request body", e);
        }
        UsernamePasswordAuthenticationToken authRequest = new UsernamePasswordAuthenticationToken(username, password);
        return authenticationManager.authenticate(authRequest);
    }

    @Override
    protected void successfulAuthentication(HttpServletRequest request, HttpServletResponse response, FilterChain chain, Authentication authResult) throws IOException {
        // The session takes the roles from the employee's position, like JpaUserDetailsService: the
        // FactorGrantedAuthority (FACTOR_PASSWORD) Spring Security 7 adds to authResult never reaches the JWT or the body
        AuthSession session = authSessionService.start(authResult.getName(), request.getHeader(HttpHeaders.USER_AGENT));
        log.info("Login successful: username='{}', authorities={}", authResult.getName(), session.body().authorities());

        // Headers before the body is written, or the response may already be committed
        response.addHeader(HEADER_AUTHORIZATION, PREFIX_TOKEN + session.body().token());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookieFactory.create(session.refreshToken()).toString());
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(CONTENT_TYPE);
        response.getWriter().write(jsonMapper.writeValueAsString(session.body()));
    }

    @Override
    protected void unsuccessfulAuthentication(HttpServletRequest request, HttpServletResponse response, AuthenticationException failed) throws IOException {
        // The exception type distinguishes the real cause behind a generic 401:
        // BadCredentialsException (password mismatch or user not found),
        // EmployeeInactiveException (active = 0), InternalAuthenticationServiceException (DB/mapping error)
        log.error("Login failed: type={}, message='{}'", failed.getClass().getSimpleName(), failed.getMessage());

        Map<String, String> body = new HashMap<>();
        body.put("message", "Login failed");
        body.put("error", failed.getMessage());

        response.getWriter().write(jsonMapper.writeValueAsString(body));
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(CONTENT_TYPE);
    }

}
