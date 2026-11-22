package com.pasadita.api.dto.auth;

import com.pasadita.api.entities.Employee;
import com.pasadita.api.security.IssuedAccessToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AuthMapper {

    public AuthResponseDto toResponseDto(Employee employee, List<GrantedAuthority> roles,
                                         IssuedAccessToken accessToken, String message) {
        return new AuthResponseDto(
                accessToken.token(),
                employee.getUsername(),
                employee.getId(),
                roles,
                message,
                accessToken.expiresAtMillis());
    }
}
