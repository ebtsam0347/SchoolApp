package com.forward.hrms.bff.service.search;

import java.time.Duration;

/**
 * Per-call context the aggregator hands to each provider. {@code callerEmployeeId}
 * is null for callers without an ESS link (admin / system users); self-only
 * providers must short-circuit when it's missing rather than 500.
 */
public record SearchCtx(
        String q,
        int limit,
        long callerUserId,
        Long callerEmployeeId,
        Duration timeout
) {}
