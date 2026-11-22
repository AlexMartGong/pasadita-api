package com.pasadita.api.dto.auth;

/**
 * Result of starting or refreshing a session: the response body and the raw refresh token, which only travels to the
 * cookie (the database keeps its hash).
 */
public record AuthSession(AuthResponseDto body, String refreshToken) {

    @Override
    public String toString() {
        return "AuthSession[body=" + body + "]";
    }
}
