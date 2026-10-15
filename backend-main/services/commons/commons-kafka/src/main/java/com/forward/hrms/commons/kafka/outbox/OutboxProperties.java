package com.forward.hrms.commons.kafka.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("hrms.outbox")
public record OutboxProperties(
        boolean enabled,
        Duration pollDelay,
        int batchSize,
        int maxRetries,
        Duration sendTimeout
) {
    public OutboxProperties {
        if (pollDelay == null) pollDelay = Duration.ofSeconds(1);
        if (batchSize == 0) batchSize = 100;
        if (maxRetries == 0) maxRetries = 10;
        if (sendTimeout == null) sendTimeout = Duration.ofSeconds(5);
    }
}
