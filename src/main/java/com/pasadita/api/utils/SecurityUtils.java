package com.pasadita.api.utils;

import com.pasadita.api.enums.user.Position;
import org.springframework.security.core.Authentication;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static boolean isAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> Position.ROLE_ADMIN.name().equals(authority.getAuthority()));
    }
}
