package com.pasadita.api.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
public class CorsConfig {

    @Value("${cors.allowed-origins:}")
    private List<String> allowedOrigins;

    @Value("${cors.allowed-origin-patterns:}")
    private List<String> allowedOriginPatterns;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Use exact origins if configured (production)
        if (allowedOrigins != null && !allowedOrigins.isEmpty() && !allowedOrigins.get(0).isEmpty()) {
            configuration.setAllowedOrigins(allowedOrigins);
        }
        // Use patterns if configured (development)
        else if (allowedOriginPatterns != null && !allowedOriginPatterns.isEmpty() && !allowedOriginPatterns.get(0).isEmpty()) {
            configuration.setAllowedOriginPatterns(allowedOriginPatterns);
        }
        // Default development patterns
        else {
            configuration.setAllowedOriginPatterns(Arrays.asList(
                    "http://localhost:*",
                    "https://localhost:*",
                    "http://127.0.0.1:*",
                    "https://127.0.0.1:*",
                    "http://192.168.*.*:*",
                    "https://192.168.*.*:*",
                    "http://10.*.*.*:*",
                    "https://10.*.*.*:*"
            ));
        }

        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Requested-With"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}