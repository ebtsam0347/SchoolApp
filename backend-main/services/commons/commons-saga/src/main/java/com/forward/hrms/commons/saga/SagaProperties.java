package com.forward.hrms.commons.saga;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Knobs for the saga substrate. Defaults are tuned for the dev k3s
 * environment — small batches, generous retry budget, modest poll
 * cadence.
 */
@ConfigurationProperties(prefix = "hrms.saga")
public record SagaProperties(
        boolean enabled,
        Duration pollDelay,
        int batchSize,
        int maxAttemptsPerStep,
        Duration backoffBase,
        Duration backoffCap
) {
    public SagaProperties {
        if (pollDelay == null) pollDelay = Duration.ofSeconds(2);
        if (batchSize <= 0) batchSize = 16;
        if (maxAttemptsPerStep <= 0) maxAttemptsPerStep = 8;
        if (backoffBase == null) backoffBase = Duration.ofSeconds(10);
        if (backoffCap == null) backoffCap = Duration.ofMinutes(15);
    }
}
