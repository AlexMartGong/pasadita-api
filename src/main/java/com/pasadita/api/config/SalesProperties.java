package com.pasadita.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.sales")
public record SalesProperties(Long walkInCustomerId) {
}
