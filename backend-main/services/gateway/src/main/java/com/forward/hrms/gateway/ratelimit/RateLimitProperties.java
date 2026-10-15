package com.forward.hrms.gateway.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Unified rate-limit config for the gateway. Two scopes:
 * <ul>
 *   <li>{@link #auth()} — per-IP on unauthenticated POSTs to
 *       {@code /auth/{login,refresh,logout}} (§6.4).</li>
 *   <li>{@link #perUser()} — per-user across path-prefix groups
 *       configured by {@link PerUser#paths()} (§6.5). Applied to
 *       {@code /audit/search} and {@code /notifications/**} by default.</li>
 * </ul>
 *
 * <p>{@code SearchRateLimitProperties} keeps its own bean for the
 * keystroke-driven {@code /bff/search} bucket — different burst profile.
 */
@ConfigurationProperties(prefix = "hrms.gateway.rate-limit")
public record RateLimitProperties(
        Auth auth,
        PerUser perUser
) {
    public RateLimitProperties {
        if (auth == null) auth = new Auth(true, 30, 30);
        if (perUser == null) perUser = new PerUser(true, 5, 10,
                List.of("/audit/search", "/notifications/**"));
    }

    /** §6.4 — per-IP bucket on the auth POSTs. */
    public record Auth(boolean enabled, int refillPerMinute, int burst) {
        public Auth {
            if (refillPerMinute <= 0) refillPerMinute = 30;
            if (burst <= 0) burst = 30;
        }
    }

    /** §6.5 — per-user bucket on noisy authenticated endpoints. */
    public record PerUser(boolean enabled, int refillPerSecond, int burst, List<String> paths) {
        public PerUser {
            if (refillPerSecond <= 0) refillPerSecond = 5;
            if (burst <= 0) burst = 10;
            if (paths == null || paths.isEmpty()) {
                paths = List.of("/audit/search", "/notifications/**");
            }
        }
    }
}
