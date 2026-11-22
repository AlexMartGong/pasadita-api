package com.pasadita.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Persistent sessions: how long a refresh token lives (sliding, renewed on every rotation) and whether its cookie
 * carries {@code Secure}. The access token's lifetime stays in {@code jwt.expiration}.
 */
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(int refreshTokenDays, boolean refreshCookieSecure) {
}
