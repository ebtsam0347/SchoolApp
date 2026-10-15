package com.forward.hrms.gateway.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "hrms.gateway.cors")
public record CorsProperties(
        List<String> allowedOrigins,
        List<String> allowedOriginPatterns,
        List<String> allowedMethods,
        List<String> allowedHeaders,
        List<String> exposedHeaders,
        boolean allowCredentials,
        long maxAgeSeconds
) {

    public CorsProperties {
        if (allowedOrigins == null) {
            allowedOrigins = List.of("http://localhost:5173", "http://localhost:3000");
        }
        if (allowedOriginPatterns == null) allowedOriginPatterns = List.of();
        if (allowedMethods == null) {
            allowedMethods = List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
        }
        if (allowedHeaders == null) {
            allowedHeaders = List.of(
                    "Authorization", "Content-Type", "Accept",
                    "X-Correlation-Id", "Idempotency-Key");
        }
        if (exposedHeaders == null) {
            exposedHeaders = List.of("X-Correlation-Id", "Location", "Retry-After");
        }
        if (maxAgeSeconds <= 0) maxAgeSeconds = 3600;
    }
}
