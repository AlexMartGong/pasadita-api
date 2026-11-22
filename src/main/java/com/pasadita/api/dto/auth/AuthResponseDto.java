package com.pasadita.api.dto.auth;

import org.springframework.security.core.GrantedAuthority;

import java.util.List;

/**
 * Body of login and refresh: the same six keys the frontend has always read from {@code POST /login}.
 * {@code authorities} serializes as {@code [{"authority":"ROLE_X"}]}; {@code expiresAt} is the access token's
 * expiration in epoch milliseconds.
 */
public record AuthResponseDto(String token,
                              String username,
                              Long employeeId,
                              List<GrantedAuthority> authorities,
                              String message,
                              long expiresAt) {

    @Override
    public String toString() {
        return "AuthResponseDto[username=" + username + ", employeeId=" + employeeId + ", authorities=" + authorities
                + ", message=" + message + ", expiresAt=" + expiresAt + "]";
    }
}
