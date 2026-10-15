package com.forward.hrms.commons.saga;

/**
 * One step in a {@link Saga}. Implementations should be idempotent:
 * retries call {@link #execute} with the same {@link SagaContext} payload.
 *
 * <p>Steps run inside a transaction managed by {@code SagaRunner}. Database
 * writes the step performs commit atomically with the saga-row update.
 * For long-running outbound calls (HTTP, etc.) the step should still
 * complete quickly — the substrate's tx is the saga row, not a business
 * tx; downstream durability is the step's responsibility.
 */
public interface SagaStep {
    String name();
    StepResult execute(SagaContext ctx);
}
