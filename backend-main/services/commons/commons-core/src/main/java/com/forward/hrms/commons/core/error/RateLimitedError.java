package com.forward.hrms.commons.core.error;

import java.time.Duration;
import java.util.Map;

public class RateLimitedError extends BusinessError {

    private final Duration retryAfter;

    public RateLimitedError(String detail, Duration retryAfter) {
        super(429, CommonErrorCode.RATE_LIMITED, detail, Map.of());
        this.retryAfter = retryAfter;
    }

    public Duration retryAfter() { return retryAfter; }
}
