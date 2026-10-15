package com.forward.hrms.payroll.service;

import com.forward.hrms.commons.saga.Saga;
import com.forward.hrms.commons.saga.SagaContext;
import com.forward.hrms.commons.saga.SagaStep;
import com.forward.hrms.commons.saga.StepResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Async tail of compute. The kickoff
 * ({@link PayrollRunService#compute}) flips the run to {@code COMPUTING}
 * and registers this saga at {@code COMPUTE_PAYSLIPS}; the runner thread
 * iterates the candidate set off the request path so 5,000+ employees
 * don't time the FE out.
 *
 * <p>Per-employee partial-failure (BatchRunner REQUIRES_NEW per row +
 * {@code payroll_run_skips}) plus {@code progress_count} pulses every
 * ~25 rows let the FE render "Computing 2,341 / 5,012" instead of a dead
 * spinner. Saga-level retry handles transient outages (employee-service
 * down for the bulk brief load, DB blip on the catalog read) — operator
 * uses {@code POST /sagas/{id}/retry} after the issue clears; rows that
 * already committed under per-row REQUIRES_NEW are skipped on the second
 * attempt by an {@code already-computed} guard.
 *
 * <p>Steps:
 * <ol>
 *   <li>{@code COMPUTE_PAYSLIPS} — drain
 *       {@code employee_payroll_settings} (excluding employees who already
 *       have a payslip on this run from a partial earlier attempt) into
 *       payslips + skip rows; flip the run {@code COMPUTING → COMPUTED};
 *       emit {@code payroll.run.computed} + audit. Idempotent.</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PayrollRunComputeSaga implements Saga {

    public static final String NAME = "PAYROLL_RUN_COMPUTE";

    private final PayrollComputeService computeService;

    @Override public String name() { return NAME; }

    @Override public List<SagaStep> steps() {
        return List.of(new ComputePayslips());
    }

    private final class ComputePayslips implements SagaStep {
        @Override public String name() { return "COMPUTE_PAYSLIPS"; }

        @Override public StepResult execute(SagaContext ctx) {
            try {
                long runId = ctx.payload().path("runId").asLong();
                Map<String, String> identity = ctx.identity();
                computeService.runComputeStep(runId, identity);
                return StepResult.done();
            } catch (RuntimeException ex) {
                log.warn("COMPUTE_PAYSLIPS retry: {}", ex.getMessage());
                return StepResult.retry(ex.getClass().getSimpleName() + ": "
                        + (ex.getMessage() == null ? "" : ex.getMessage()));
            }
        }
    }
}
