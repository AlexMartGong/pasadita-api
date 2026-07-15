package com.pasadita.api.security.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pasadita.api.entities.Employee;
import com.pasadita.api.repositories.EmployeeRepository;
import com.pasadita.api.security.TokenJwtConfig;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.FilterChain;
import lombok.extern.slf4j.Slf4j;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static com.pasadita.api.security.TokenJwtConfig.*;

@Slf4j
public class JwtAuthenticationFilter extends UsernamePasswordAuthenticationFilter {

    private final AuthenticationManager authenticationManager;
    private final TokenJwtConfig tokenJwtConfig;
    private final EmployeeRepository employeeRepository;

    public JwtAuthenticationFilter(AuthenticationManager authenticationManager, TokenJwtConfig tokenJwtConfig, EmployeeRepository employeeRepository) {
        this.authenticationManager = authenticationManager;
        this.tokenJwtConfig = tokenJwtConfig;
        this.employeeRepository = employeeRepository;
        // POST-only matcher: setFilterProcessesUrl matches every HTTP method,
        // which let CORS preflight OPTIONS reach attemptAuthentication and
        // fail parsing its empty body
        setRequiresAuthenticationRequestMatcher(
                PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/login"));
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) throws AuthenticationException {
        Employee employee = null;
        String username = null;
        String password = null;

        try {
            employee = new ObjectMapper().readValue(request.getInputStream(), Employee.class);
            username = employee.getUsername();
            password = employee.getPassword();
            // Never log the raw password — length + whitespace flag is enough
            // to spot empty/duplicated/padded credentials coming from the frontend
            log.info("Login attempt: username='{}' (usernameLength={}), passwordLength={}, passwordHasWhitespace={}",
                    username,
                    username != null ? username.length() : -1,
                    password != null ? password.length() : -1,
                    password != null && !password.equals(password.strip()));
        } catch (IOException e) {
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
        log.info("Login successful: username='{}', authorities={}", authResult.getName(), authResult.getAuthorities());

        Date expirationDate = tokenJwtConfig.getExpirationDate();

        String token = Jwts.builder()
                .subject(authResult.getName())
                .claim("authorities", authResult.getAuthorities())
                .issuedAt(new Date())
                .expiration(expirationDate)
                .signWith(tokenJwtConfig.getSecretKey())
                .compact();
        response.addHeader(HEADER_AUTHORIZATION, PREFIX_TOKEN + token);

        // Get employee ID
        Employee employee = employeeRepository.findByUsername(authResult.getName())
                .orElse(null);

        Map<String, Object> body = new HashMap<>();
        body.put("token", token);
        body.put("username", authResult.getName());
        if (employee != null) {
            body.put("employeeId", employee.getId());
        }
        body.put("authorities", authResult.getAuthorities());
        body.put("message", "Login successful");
        body.put("expiresAt", expirationDate.getTime());

        response.getWriter().write(new ObjectMapper().writeValueAsString(body));
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(TokenJwtConfig.CONTENT_TYPE);

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

        response.getWriter().write(new ObjectMapper().writeValueAsString(body));
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(CONTENT_TYPE);
    }

}
