package com.pasadita.api.security;

/**
 * A signed access JWT and its expiration as epoch milliseconds (the login body's {@code expiresAt}).
 */
public record IssuedAccessToken(String token, long expiresAtMillis) {

    @Override
    public String toString() {
        return "IssuedAccessToken[expiresAtMillis=" + expiresAtMillis + "]";
    }
}
