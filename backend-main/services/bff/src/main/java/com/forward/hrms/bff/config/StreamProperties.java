package com.forward.hrms.bff.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Tuning knobs for the live-notification SSE stream. Heartbeat cadence
 * is fixed in {@code NotificationStreamService}; only the per-connection
 * timeout is configurable here, since "how long do we hold a stuck
 * connection before reaping it" can vary by environment.
 */
@ConfigurationProperties(prefix = "hrms.bff.notifications.stream")
public record StreamProperties(
        Duration timeout
) {
    public StreamProperties {
        if (timeout == null) timeout = Duration.ofMinutes(10);
    }
}
