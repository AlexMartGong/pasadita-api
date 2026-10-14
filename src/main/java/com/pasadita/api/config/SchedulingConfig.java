package com.pasadita.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

/**
 * Enables {@code @Scheduled} tasks (printer WebSocket heartbeat) and exposes the time source they use.
 * Kept apart from {@link WebSocketConfig}, which injects the handler that depends on this {@link Clock}.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
