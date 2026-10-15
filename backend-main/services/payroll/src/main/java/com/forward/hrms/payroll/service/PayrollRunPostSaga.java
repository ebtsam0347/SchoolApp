package com.forward.hrms.payroll.service;

import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.kafka.outbox.Outbox;
import com.forward.hrms.commons.saga.Saga;
import com.forward.hrms.commons.saga.SagaContext;
import com.forward.hrms.commons.saga.SagaStep;
import com.forward.hrms.commons.saga.StepResult;
import com.forward.hrms.payroll.entity.PayrollRun;
import com.forward.hrms.payroll.enums.PayrollRunStatus;
import com.forward.hrms.payroll.events.PayrollEvents;
import com.forward.hrms.payroll.repository.PayrollRunRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Async tail of the post step. The kickoff
 * ({@link PayrollRunService#post}) flips the run to {@code POSTING} and
 * starts this saga at {@code GENERATE_PDFS}. The runner thread carries
 * each step through to completion, retrying with backoff on transient
 * failure (MinIO blip, Kafka outage) and bailing into {@code FAILED} on
 * the per-step retry budget so HR can re-trigger via
 * {@code POST /sagas/{id}/retry}.
 *
 * <p>Steps:
 * <ol>
 *   <li>{@code GENERATE_PDFS} — Jasper-render every payslip on the run
 *       that has no {@code pdf_object_key} yet, upload to MinIO, emit one
 *       {@code payroll.payslip.generated} per. Idempotent: payslips with
 *       a key are skipped.</li>
 *   <li>{@code FINALIZE_RUN} — flip {@code RELEASE_PENDING} sources to
 *       {@code RELEASED}, mark run {@code POSTED}, emit
 *       {@code payroll.run.posted}. Idempotent: re-running on a posted
 *       run is a no-op.</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PayrollRunPostSaga implements Saga {

    public static final String NAME = "PAYROLL_RUN_POST";

    private final PayslipPdfService pdfService;
    private final PayrollComputeService computeService;
    private final PayrollRunRepository runs;
    private final Outbox outbox;
    private final AuditEmitter audit;

    @Override public String name() { return NAME; }

    @Override public List<SagaStep> steps() {
        return List.of(new GeneratePdfs(), new FinalizeRun());
    }

    // commons-saga's SagaRunner.advanceOne is @Transactional, so each step
    // already runs inside an outer tx — no per-step annotation needed (and
    // any annotation on a private inner-class instance method wouldn't fire
    // anyway: no Spring proxy + self-invocation).
    private final class GeneratePdfs implements SagaStep {
        @Override public String name() { return "GENERATE_PDFS"; }

        @Override public StepResult execute(SagaContext ctx) {
            try {
                long runId = ctx.payload().path("runId").asLong();
                PayrollRun r = loadRunOrFail(runId);
                Map<String, String> identity = ctx.identity();
                pdfService.generateAll(r, identity);
                return StepResult.done();
            } catch (RuntimeException ex) {
                log.warn("GENERATE_PDFS retry: {}", ex.getMessage());
                return StepResult.retry(ex.getClass().getSimpleName() + ": "
                        + (ex.getMessage() == null ? "" : ex.getMessage()));
            }
        }
    }

    private final class FinalizeRun implements SagaStep {
        @Override public String name() { return "FINALIZE_RUN"; }

        @Override public StepResult execute(SagaContext ctx) {
            try {
                long runId = ctx.payload().path("runId").asLong();
                PayrollRun r = loadRunOrFail(runId);
                if (r.getStatus() == PayrollRunStatus.POSTED) return StepResult.done();   // idempotent

                computeService.markReleasedSourcesPosted(r);
                r.setStatus(PayrollRunStatus.POSTED);
                r.setPostedAt(LocalDateTime.now());

                outbox.publish(PayrollEvents.TOPIC, "payroll_run:" + runId,
                        PayrollEvents.RUN_POSTED,
                        new PayrollEvents.RunPosted(runId, r.getFiscalYearCode()));
                audit.emit("payroll_run", String.valueOf(runId), "run_posted", null,
                        Map.of("postedAt", r.getPostedAt().toString()), true);
                return StepResult.done();
            } catch (RuntimeException ex) {
                log.warn("FINALIZE_RUN retry: {}", ex.getMessage());
                return StepResult.retry(ex.getClass().getSimpleName() + ": "
                        + (ex.getMessage() == null ? "" : ex.getMessage()));
            }
        }
    }

    private PayrollRun loadRunOrFail(long runId) {
        return runs.findById(runId).orElseThrow(
                () -> new IllegalStateException("payroll run " + runId + " disappeared mid-saga"));
    }
}
