package com.forward.hrms.commons.batch;

import java.util.List;

/**
 * Per-row results from a {@link BatchRunner#runWithPartial} call.
 *
 * <p>{@code succeeded} and {@code failed} are positional — index N in
 * either list refers to the same {@code item} (failures carry their
 * input alongside the error). Use {@link #succeededCount()} for
 * fast-path metrics; use {@link #failed()} when you need to surface
 * per-row reasons in an event payload, audit row, or response body.
 */
public record BatchOutcomes<I, R>(
        List<Success<I, R>> succeeded,
        List<Failure<I>> failed
) {

    public int succeededCount() { return succeeded.size(); }
    public int failedCount() { return failed.size(); }
    public int totalAttempted() { return succeeded.size() + failed.size(); }
    public boolean allSucceeded() { return failed.isEmpty(); }
    public boolean allFailed() { return succeeded.isEmpty() && !failed.isEmpty(); }

    /** Successful per-row result. {@code result} may be null if the worker returns void. */
    public record Success<I, R>(I item, R result) {}

    /**
     * Failed per-row attempt. {@code errorCode} is the
     * {@code BusinessError.code().name()} when the cause is a known
     * commons error (so callers can surface a stable identifier);
     * for unknown causes it's {@code "INTERNAL"}.
     */
    public record Failure<I>(I item, String errorCode, String message, Throwable cause) {}
}
