package com.pasadita.api.exceptions;

/**
 * A rotated refresh token came back after the grace window: the whole family was revoked (committed despite this
 * exception) and the answer is 401 with {@code error = refresh_token_reused}.
 */
public class RefreshTokenReusedException extends InvalidRefreshTokenException {

    private static final String REFRESH_TOKEN_REUSED = "refresh_token_reused";

    public RefreshTokenReusedException() {
        super(REFRESH_TOKEN_REUSED, "Se detectó un uso repetido de la sesión y se cerró por seguridad. Inicia sesión de nuevo.");
    }
}
