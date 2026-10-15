package com.forward.hrms.commons.saga;

/**
 * Outcome of one {@link SagaStep#execute} invocation. Exhaustive sum type:
 * the runner uses pattern matching to advance / retry / fail.
 *
 * <p>{@link Retry} is for transient errors (timeouts, 5xx). The runner
 * applies exponential backoff and gives up after the configured
 * {@code hrms.saga.max-attempts-per-step}, flipping the saga to FAILED.
 *
 * <p>{@link Failed} is for non-recoverable errors (permanent 4xx,
 * validation). The runner flips the saga to FAILED immediately.
 */
public sealed interface StepResult permits StepResult.Done, StepResult.Retry, StepResult.Failed {

    record Done() implements StepResult {}
    record Retry(String reason) implements StepResult {}
    record Failed(String code, String message) implements StepResult {}

    static StepResult done() { return new Done(); }
    static StepResult retry(String reason) { return new Retry(reason); }
    static StepResult failed(String code, String message) { return new Failed(code, message); }
}
