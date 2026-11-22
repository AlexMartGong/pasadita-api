package com.pasadita.api.services.auth;

import com.pasadita.api.dto.auth.AuthSession;

/**
 * Lifecycle of persistent sessions: an access JWT plus a rotating opaque refresh token per login (family).
 */
public interface AuthSessionService {

    /**
     * Opens a session for an already authenticated employee: new family, first refresh token, access token.
     */
    AuthSession start(String username, String userAgent);

    /**
     * Rotates the presented refresh token and issues a new access token.
     *
     * @throws com.pasadita.api.exceptions.InvalidRefreshTokenException       missing, unknown, expired or revoked token
     * @throws com.pasadita.api.exceptions.RefreshTokenReusedException        rotated token replayed after the grace
     *                                                                        window (its family is revoked)
     * @throws com.pasadita.api.exceptions.EmployeeInactiveException          the employee was deactivated
     */
    AuthSession refresh(String refreshToken, String userAgent);

    /**
     * Ends the session (family) of the presented token; a missing or unknown token is a no-op.
     */
    void logout(String refreshToken);

    /**
     * Revokes every refresh token of the employee (deactivation, password change). Joins the caller's transaction.
     */
    void revokeAllSessions(Long employeeId);
}
