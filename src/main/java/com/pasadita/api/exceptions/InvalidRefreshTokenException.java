package com.pasadita.api.exceptions;

import lombok.Getter;

/**
 * The refresh cookie is missing, unknown, expired or revoked: 401 with {@code error = invalid_refresh_token}.
 */
@Getter
public class InvalidRefreshTokenException extends RuntimeException {

    private static final String INVALID_REFRESH_TOKEN = "invalid_refresh_token";

    private final String code;

    public InvalidRefreshTokenException() {
        this(INVALID_REFRESH_TOKEN, "La sesión expiró o no es válida. Inicia sesión de nuevo.");
    }

    protected InvalidRefreshTokenException(String code, String message) {
        super(message);
        this.code = code;
    }
}
