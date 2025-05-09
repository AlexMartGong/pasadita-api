package com.pasadita.api.security.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pasadita.api.security.SimpleGrantedAuthorityJsonCreator;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static com.pasadita.api.security.TokenJwtConfig.*;

public class JwtValidationFilter extends BasicAuthenticationFilter {

    private final ObjectMapper objectMapper;

    public JwtValidationFilter(AuthenticationManager authenticationManager) {
        super(authenticationManager);
        this.objectMapper = new ObjectMapper().addMixIn(SimpleGrantedAuthority.class, SimpleGrantedAuthorityJsonCreator.class);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
        String header = request.getHeader(HEADER_AUTHORIZATION);

        if (header == null || !header.startsWith(PREFIX_TOKEN)) {
            chain.doFilter(request, response);
            return;
        }

        String token = header.replace(PREFIX_TOKEN, "");

        try {
            Claims claims = Jwts.parser().verifyWith(SECRET_KEY).build().parseSignedClaims(token).getPayload();
            String username = claims.getSubject();

            Collection<? extends GrantedAuthority> authorities;
            Object authoritiesClaim = claims.get("authorities");

            if (authoritiesClaim instanceof String) {
                authorities = Arrays.asList(objectMapper.readValue((String) authoritiesClaim, SimpleGrantedAuthority[].class));
            } else if (authoritiesClaim instanceof List<?> authList) {
                String authJson = objectMapper.writeValueAsString(authList);
                authorities = Arrays.asList(objectMapper.readValue(authJson, SimpleGrantedAuthority[].class));
            } else {
                String authJson = objectMapper.writeValueAsString(authoritiesClaim);
                authorities = Arrays.asList(objectMapper.readValue(authJson, SimpleGrantedAuthority[].class));
            }

            UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(username, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authenticationToken);
            chain.doFilter(request, response);
        } catch (JwtException | IllegalArgumentException | IOException | ServletException e) {
            Map<String, String> body = Map.of(
                    "message", "Invalid token",
                    "error", e.getMessage()
            );
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(CONTENT_TYPE);
            response.getWriter().write(objectMapper.writeValueAsString(body));
        }

    }
}
