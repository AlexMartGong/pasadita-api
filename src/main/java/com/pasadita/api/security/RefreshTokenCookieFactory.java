package com.pasadita.api.security;

import com.pasadita.api.config.AuthProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * The refresh token cookie: httpOnly, SameSite=Lax, persistent (Max-Age = refresh token lifetime), host-only (no
 * Domain) and scoped to {@value #PATH}, so the browser sends it only to refresh and logout. Clearing it must repeat
 * the same name, Path, Secure and SameSite, or the browser keeps the old one.
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenCookieFactory {

    public static final String COOKIE_NAME = "refresh_token";
    public static final String PATH = "/api/auth";
    private static final String SAME_SITE = "Lax";

    private final AuthProperties authProperties;

    public ResponseCookie create(String refreshToken) {
        return baseCookie(refreshToken)
                .maxAge(Duration.ofDays(authProperties.refreshTokenDays()))
                .build();
    }

    public ResponseCookie clear() {
        return baseCookie("")
                .maxAge(Duration.ZERO)
                .build();
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(authProperties.refreshCookieSecure())
                .sameSite(SAME_SITE)
                .path(PATH);
    }
}
