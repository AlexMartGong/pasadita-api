package com.pasadita.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cloudflare.r2")
public record R2Properties(
        String accessKey,
        String secretKey,
        String endpoint,
        String bucket,
        String publicUrl
) {
}
