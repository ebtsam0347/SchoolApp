package com.forward.hrms.commons.saga;

import java.util.List;

/**
 * Service-neutral saga health rollup. The dashboard composite consumes
 * this from each writing service ({@code /sagas/health},
 * {@code /payroll/sagas/health}) and merges the totals.
 *
 * <p>{@code failureRate24h} is exactly 24 hourly buckets ending at the
 * current hour ({@code [now-23h, now-22h, …, now]}), counting FAILED
 * transitions in each bucket. Bucket index 23 is the freshest hour.
 */
public record SagaHealthSnapshot(
        int running,
        int failed24h,
        List<PerName> perName
) {
    public record PerName(String name, String status, List<Integer> failureRate24h) {}
}
