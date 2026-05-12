package com.pasadita.api.config;

import io.facturapi.Facturapi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class FacturapiConfig {

    @Bean
    public Facturapi facturapi(@Value("${facturapi.key:}") String apiKey) {
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException(
                    "Property 'facturapi.key' (env FACTURAPI_KEY) is required to enable CFDI stamping");
        }
        return Facturapi.builder(apiKey)
                .apiVersion("v2")
                .timeout(Duration.ofSeconds(20))
                .build();
    }

    @Bean
    public HttpClient facturapiHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
    }
}
