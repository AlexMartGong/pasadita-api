package com.pasadita.api.security;

import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

/**
 * Builds the access JWT for login and refresh alike, so both carry the claims {@code JwtValidationFilter} reads:
 * {@code sub} = username and {@code authorities} = a list of {@code SimpleGrantedAuthority}, serialized as
 * {@code [{"authority":"ROLE_X"}]} (a plain string list would fail every later request).
 */
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final TokenJwtConfig tokenJwtConfig;

    public IssuedAccessToken issue(String username, List<? extends GrantedAuthority> roles) {
        Date expirationDate = tokenJwtConfig.getExpirationDate();
        String token = Jwts.builder()
                .subject(username)
                .claim("authorities", roles)
                .issuedAt(new Date())
                .expiration(expirationDate)
                .signWith(tokenJwtConfig.getSecretKey())
                .compact();
        return new IssuedAccessToken(token, expirationDate.getTime());
    }
}
