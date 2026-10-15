package com.forward.hrms.commons.web.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Outbound HTTP client knobs shared by every service. Defaults are tuned
 * for the FSHRMS topology — short connect (any backend in the same k3s
 * cluster should accept inside a second; 5 s allows a cold pod start),
 * moderate read (large reports + bulk endpoints can take a few seconds
 * but the request thread shouldn't block forever).
 *
 * <p>Override per service via
 * {@code hrms.web.client.connect-timeout=2s} and
 * {@code hrms.web.client.read-timeout=30s}.
 */
@ConfigurationProperties("hrms.web.client")
public record WebClientProperties(
        Duration connectTimeout,
        Duration readTimeout
) {
    public WebClientProperties {
        if (connectTimeout == null) connectTimeout = Duration.ofSeconds(5);
        if (readTimeout == null) readTimeout = Duration.ofSeconds(10);
    }
}
