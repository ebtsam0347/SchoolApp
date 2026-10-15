package com.forward.hrms.gateway.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Per-caller bucket on {@code GET /bff/search} only — palette is keystroke-
 * driven and would otherwise starve neighbour {@code /bff/*} endpoints if
 * the limit was shared.
 */
@ConfigurationProperties(prefix = "hrms.gateway.search-rate-limit")
public record SearchRateLimitProperties(
        boolean enabled,
        int refillPerSecond,
        int burst
) {
    public SearchRateLimitProperties {
        if (refillPerSecond <= 0) refillPerSecond = 10;
        if (burst <= 0) burst = 20;
    }

    public static SearchRateLimitProperties defaults() {
        return new SearchRateLimitProperties(true, 10, 20);
    }
}
