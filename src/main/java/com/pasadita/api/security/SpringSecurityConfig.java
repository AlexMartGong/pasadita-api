package com.pasadita.api.security;

import com.pasadita.api.security.filter.JwtAuthenticationFilter;
import com.pasadita.api.security.filter.JwtValidationFilter;
import com.pasadita.api.services.auth.AuthSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;
import tools.jackson.databind.json.JsonMapper;

@Configuration
@EnableMethodSecurity(securedEnabled = true, jsr250Enabled = true)
@RequiredArgsConstructor
public class SpringSecurityConfig {

    private final AuthenticationConfiguration authenticationConfiguration;
    private final CorsConfigurationSource corsConfigurationSource;
    private final TokenJwtConfig tokenConfig;

    @Bean
    AuthenticationManager authenticationManager() throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, JsonMapper jsonMapper, AuthSessionService authSessionService,
                                    RefreshTokenCookieFactory refreshTokenCookieFactory) throws Exception {
        return http.cors(cors ->
                        cors.configurationSource(corsConfigurationSource))
                .authorizeHttpRequests((auth) ->
                        auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                                .requestMatchers("/ws/**").permitAll()
                                // The refresh cookie is the credential: the access token may already be expired
                                .requestMatchers(HttpMethod.POST, "/api/auth/refresh", "/api/auth/logout").permitAll()
                                .anyRequest().authenticated()
                ).addFilter(new JwtAuthenticationFilter(authenticationManager(), authSessionService,
                        refreshTokenCookieFactory, jsonMapper))
                .addFilter(new JwtValidationFilter(authenticationManager(), tokenConfig))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(management ->
                        management.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .build();
    }

}
