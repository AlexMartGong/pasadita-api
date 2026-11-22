package com.pasadita.api.controllers;

import com.pasadita.api.dto.auth.AuthResponseDto;
import com.pasadita.api.dto.auth.AuthSession;
import com.pasadita.api.security.RefreshTokenCookieFactory;
import com.pasadita.api.security.TokenJwtConfig;
import com.pasadita.api.services.auth.AuthSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public on purpose (permitAll, skipped by JwtValidationFilter): the caller's access token may already be expired.
 * The refresh token cookie is the credential.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthSessionService authSessionService;
    private final RefreshTokenCookieFactory refreshTokenCookieFactory;

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponseDto> refresh(
            @CookieValue(name = RefreshTokenCookieFactory.COOKIE_NAME, required = false) String refreshToken,
            @RequestHeader(name = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        AuthSession session = authSessionService.refresh(refreshToken, userAgent);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieFactory.create(session.refreshToken()).toString())
                .header(HttpHeaders.AUTHORIZATION, TokenJwtConfig.PREFIX_TOKEN + session.body().token())
                .body(session.body());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = RefreshTokenCookieFactory.COOKIE_NAME, required = false) String refreshToken) {
        authSessionService.logout(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieFactory.clear().toString())
                .build();
    }
}
