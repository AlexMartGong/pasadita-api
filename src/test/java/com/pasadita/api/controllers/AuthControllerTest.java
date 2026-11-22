package com.pasadita.api.controllers;

import com.pasadita.api.config.AuthProperties;
import com.pasadita.api.dto.auth.AuthResponseDto;
import com.pasadita.api.dto.auth.AuthSession;
import com.pasadita.api.exceptions.EmployeeInactiveException;
import com.pasadita.api.exceptions.GlobalExceptionHandler;
import com.pasadita.api.exceptions.InvalidRefreshTokenException;
import com.pasadita.api.exceptions.RefreshTokenReusedException;
import com.pasadita.api.security.RefreshTokenCookieFactory;
import com.pasadita.api.services.auth.AuthSessionService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock private AuthSessionService authSessionService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        RefreshTokenCookieFactory cookieFactory = new RefreshTokenCookieFactory(new AuthProperties(60, true));
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authSessionService, cookieFactory))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void refreshReadsTheCookieAndAnswersWithTheLoginBodyAndARotatedCookie() throws Exception {
        AuthResponseDto body = new AuthResponseDto("new-jwt", "admin", 1L,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")), "Token refreshed", 1_790_000_000_000L);
        when(authSessionService.refresh("old-refresh", "Mozilla/5.0")).thenReturn(new AuthSession(body, "new-refresh"));

        MvcResult result = mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("refresh_token", "old-refresh"))
                        .header(HttpHeaders.USER_AGENT, "Mozilla/5.0"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.AUTHORIZATION, "Bearer new-jwt"))
                .andExpect(jsonPath("$.token").value("new-jwt"))
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.employeeId").value(1))
                .andExpect(jsonPath("$.authorities[0].authority").value("ROLE_ADMIN"))
                .andExpect(jsonPath("$.message").value("Token refreshed"))
                .andExpect(jsonPath("$.expiresAt").value(1_790_000_000_000L))
                .andReturn();

        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE))
                .startsWith("refresh_token=new-refresh;")
                .contains("Path=/api/auth", "Max-Age=5184000", "HttpOnly", "Secure", "SameSite=Lax");
    }

    @Test
    void refreshWithoutACookieIsA401InvalidRefreshToken() throws Exception {
        when(authSessionService.refresh(isNull(), any())).thenThrow(new InvalidRefreshTokenException());

        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_refresh_token"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void reusedRefreshTokenIsA401RefreshTokenReused() throws Exception {
        when(authSessionService.refresh(any(), any())).thenThrow(new RefreshTokenReusedException());

        mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie("refresh_token", "rotated")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("refresh_token_reused"));
    }

    @Test
    void refreshOfADeactivatedEmployeeAnswersLikeItsLogin() throws Exception {
        when(authSessionService.refresh(any(), any())).thenThrow(new EmployeeInactiveException("Usuario inactivo"));

        mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie("refresh_token", "valid")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Login failed"))
                .andExpect(jsonPath("$.error").value("Usuario inactivo"));
    }

    @Test
    void logoutRevokesTheSessionAndClearsTheCookieWithTheSameAttributes() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/logout").cookie(new Cookie("refresh_token", "current")))
                .andExpect(status().isNoContent())
                .andReturn();

        verify(authSessionService).logout("current");
        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE))
                .startsWith("refresh_token=;")
                .contains("Path=/api/auth", "Max-Age=0", "HttpOnly", "Secure", "SameSite=Lax");
    }

    @Test
    void logoutWithoutACookieIsStillA204() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isNoContent())
                .andExpect(header().exists(HttpHeaders.SET_COOKIE));

        verify(authSessionService).logout(null);
    }
}
