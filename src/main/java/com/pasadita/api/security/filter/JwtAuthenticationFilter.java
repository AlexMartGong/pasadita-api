package com.pasadita.api.security.filter;

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
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.pasadita.api.security.TokenJwtConfig.*;

@Slf4j
public class JwtAuthenticationFilter extends UsernamePasswordAuthenticationFilter {

    private final AuthenticationManager authenticationManager;
    private final TokenJwtConfig tokenJwtConfig;
    private final EmployeeRepository employeeRepository;
    // Spring Boot's mapper, not a raw JsonMapper: Jackson 3's defaults bind through the Lombok
    // all-args constructor, so the absent primitive `active` made every login body fail
    private final JsonMapper jsonMapper;

    public JwtAuthenticationFilter(AuthenticationManager authenticationManager, TokenJwtConfig tokenJwtConfig,
                                   EmployeeRepository employeeRepository, JsonMapper jsonMapper) {
        this.authenticationManager = authenticationManager;
        this.tokenJwtConfig = tokenJwtConfig;
        this.employeeRepository = employeeRepository;
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
        // Spring Security 7 adds a FactorGrantedAuthority (FACTOR_PASSWORD, carrying an Instant) to every
        // authentication: keep only the employee's roles so the JWT claim and the login body stay unchanged
        List<GrantedAuthority> roles = authResult.getAuthorities().stream()
                .filter(authority -> !(authority instanceof FactorGrantedAuthority))
                .collect(Collectors.toList());
        log.info("Login successful: username='{}', authorities={}", authResult.getName(), roles);

        Date expirationDate = tokenJwtConfig.getExpirationDate();

        String token = Jwts.builder()
                .subject(authResult.getName())
                .claim("authorities", roles)
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
        body.put("authorities", roles);
        body.put("message", "Login successful");
        body.put("expiresAt", expirationDate.getTime());

        response.getWriter().write(jsonMapper.writeValueAsString(body));
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

        response.getWriter().write(jsonMapper.writeValueAsString(body));
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(CONTENT_TYPE);
    }

}
