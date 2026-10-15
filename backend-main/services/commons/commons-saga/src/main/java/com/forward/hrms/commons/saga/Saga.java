package com.forward.hrms.commons.saga;

import java.util.List;

/**
 * One persistent, multi-step orchestration. The substrate is forward-only
 * with bounded retry per step — there is no compensation registry. A step
 * that returns {@link StepResult.Failed} (or exhausts retries) terminates
 * the saga in {@code FAILED}; recovery is manual via {@code POST /sagas/{id}/retry}
 * or a domain-specific operator action.
 *
 * <p>Each saga is a Spring bean discovered by {@link SagaRegistry} via
 * {@link #name()}. Names are the canonical key in {@code saga_state.saga_name}
 * and must be stable across deployments.
 */
public interface Saga {
    String name();
    List<SagaStep> steps();
}
