package com.forward.hrms.payroll.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.kafka.outbox.Outbox;
import com.forward.hrms.commons.saga.SagaContext;
import com.forward.hrms.commons.saga.SagaService;
import com.forward.hrms.commons.web.page.PageResponse;
import com.forward.hrms.payroll.client.EmployeeClient;
import com.forward.hrms.payroll.client.AttendanceClient;
import com.forward.hrms.payroll.dto.request.CreatePayrollRunRequest;
import com.forward.hrms.payroll.dto.response.PayrollRunDto;
import com.forward.hrms.payroll.dto.response.PayslipDto;
import com.forward.hrms.payroll.entity.FiscalYear;
import com.forward.hrms.payroll.entity.PayrollRun;
import com.forward.hrms.payroll.enums.PayrollErrorCode;
import com.forward.hrms.payroll.enums.PayrollRunStatus;
import com.forward.hrms.payroll.enums.PayslipStatus;
import com.forward.hrms.payroll.events.PayrollEvents;
import com.forward.hrms.payroll.dto.response.PayrollRunSkipDto;
import com.forward.hrms.payroll.repository.PayrollRunRepository;
import com.forward.hrms.payroll.repository.PayrollRunSkipRepository;
import com.forward.hrms.payroll.repository.PayslipRepository;
import com.forward.hrms.payroll.dto.response.PayslipDtoMapper;
import com.forward.hrms.payroll.dto.response.PayrollRunDtoMapper;
import com.forward.hrms.payroll.dto.response.PayrollRunSkipDtoMapper;
import com.forward.hrms.payroll.dto.response.PayrollRunTotalsDtoMapper;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class PayrollRunService {

    private final PayrollRunRepository runs;
    private final PayslipRepository payslips;
    private final PayrollRunSkipRepository skips;
    private final com.forward.hrms.payroll.repository.PermanentAdvanceDeductionRepository permanentAdvanceDeductions;
    private final com.forward.hrms.payroll.repository.RfdAdvanceRepository rfdAdvances;
    private final FiscalYearService fiscalYears;
    private final PayrollComputeService compute;
    private final AttendanceClient attendance;
    private final SagaService sagaService;
    private final ObjectMapper sagaJson;
    private final Outbox outbox;
    private final AuditEmitter audit;
    @PersistenceContext
    private EntityManager em;

    public PayrollRunService(PayrollRunRepository runs,
                             PayslipRepository payslips,
                             PayrollRunSkipRepository skips,
                             com.forward.hrms.payroll.repository.PermanentAdvanceDeductionRepository permanentAdvanceDeductions,
                             com.forward.hrms.payroll.repository.RfdAdvanceRepository rfdAdvances,
                             FiscalYearService fiscalYears,
                             PayrollComputeService compute,
                             AttendanceClient attendance,
                             SagaService sagaService,
                             @Qualifier("sagaObjectMapper") ObjectMapper sagaJson,
                             Outbox outbox,
                             AuditEmitter audit) {
        this.runs = runs;
        this.payslips = payslips;
        this.skips = skips;
        this.permanentAdvanceDeductions = permanentAdvanceDeductions;
        this.rfdAdvances = rfdAdvances;
        this.fiscalYears = fiscalYears;
        this.compute = compute;
        this.attendance = attendance;
        this.sagaService = sagaService;
        this.sagaJson = sagaJson;
        this.outbox = outbox;
        this.audit = audit;
    }

    @Transactional
    public PayrollRunDto create(CreatePayrollRunRequest req) {
        if (runs.existsByPeriodYearAndPeriodMonth(req.year(), req.month())) {
            throw new ConflictError(PayrollErrorCode.PAYROLL_RUN_DUPLICATE,
                    "a run already exists for " + req.year() + "-" +
                            String.format("%02d", req.month()));
        }
        // §7.payroll #16 — refuse periods more than one month in the future.
        // Forward-dating a run by years was harmless but confusing; cap to
        // "current month + 1" so HR can prep next month without opening a
        // 2030 row by mistake.
        java.time.YearMonth requested = java.time.YearMonth.of(req.year(), req.month());
        java.time.YearMonth currentPlusOne = java.time.YearMonth.now().plusMonths(1);
        if (requested.isAfter(currentPlusOne)) {
            throw new com.forward.hrms.commons.core.error.UnprocessableError(
                    PayrollErrorCode.INVALID_PERIOD,
                    "period " + requested + " is more than one month in the future");
        }
        FiscalYear fy = fiscalYears.forPeriod(req.year(), req.month());
        PayrollRun r = new PayrollRun();
        r.setFiscalYearCode(fy.getCode());
        r.setPeriodYear(req.year());
        r.setPeriodMonth(req.month());
        r.setStatus(PayrollRunStatus.DRAFT);
        r = runs.save(r);

        outbox.publish(PayrollEvents.TOPIC, "payroll_run:" + r.getId(),
                PayrollEvents.RUN_CREATED,
                new PayrollEvents.RunCreated(r.getId(), fy.getCode(), r.getPeriodYear(), r.getPeriodMonth()));
        audit.emit("payroll_run", String.valueOf(r.getId()), "run_created", null,
                Map.of("fiscalYear", fy.getCode(),
                        "year", r.getPeriodYear(), "month", r.getPeriodMonth()),
                true);
        return PayrollRunDtoMapper.of(r);
    }

    @Transactional(readOnly = true)
    public PayrollRunDto get(long id) {
        return PayrollRunDtoMapper.of(load(id));
    }

    @Transactional(readOnly = true)
    public List<PayrollRunDto> listByFiscalYear(String fiscalYearCode) {
        return runs.findAllByFiscalYearCodeOrderByPeriodYearDescPeriodMonthDesc(fiscalYearCode)
                .stream().map(PayrollRunDtoMapper::of).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<PayslipDto> payslips(long runId, PayslipStatus status, String q, Pageable pageable) {
        load(runId);
        String trimmed = (q == null || q.isBlank()) ? null : q.trim();
        return PageResponse.from(
                payslips.searchByRun(runId, status, trimmed, pageable).map(PayslipDtoMapper::of));
    }

    @Transactional(readOnly = true)
    public com.forward.hrms.payroll.dto.response.PayrollRunTotalsDto totals(long runId, PayslipStatus status, String q) {
        load(runId);
        String trimmed = (q == null || q.isBlank()) ? null : q.trim();
        return com.forward.hrms.payroll.dto.response.PayrollRunTotalsDtoMapper.of(
                runId, payslips.totalsForRun(runId, status, trimmed));
    }

    /**
     * One-shot live snapshot for FE polling during compute / post.
     * Returns run status + live counts (pulsed every ~25 rows in
     * {@link PayrollComputeService#runComputeStep}) plus the SQL-aggregated
     * payslip totals. The totals are live because payslips commit per-row
     * REQUIRES_NEW mid-run; the FE drops its
     * {@code GET /runs/{id}} + {@code GET /runs/{id}/totals} two-call
     * pattern for this one URL.
     */
    @Transactional(readOnly = true)
    public com.forward.hrms.payroll.dto.response.PayrollRunSummaryDto summary(long runId) {
        PayrollRun r = load(runId);
        com.forward.hrms.payroll.dto.response.PayrollRunTotalsDto totals =
                com.forward.hrms.payroll.dto.response.PayrollRunTotalsDtoMapper.of(
                        runId, payslips.totalsForRun(runId, null, null));
        int success = Math.max(0, r.getProgressCount() - r.getSkippedCount());
        return new com.forward.hrms.payroll.dto.response.PayrollRunSummaryDto(
                r.getId(), r.getFiscalYearCode(), r.getPeriodYear(), r.getPeriodMonth(),
                r.getStatus(), r.getComputedAt(), r.getPostedAt(),
                r.getTotalCount(), r.getProgressCount(), r.getSkippedCount(),
                success, totals);
    }

    /**
     * Lock the run's period for payroll. The operator owns the lock now —
     * they close the month from the attendance side, which emits
     * {@code attendance.period_locked}. This step only verifies that lock
     * and flips {@code DRAFT → LOCKED}; it does not command attendance to
     * lock. The attendance event listener already advances a DRAFT
     * run automatically when the event arrives, but a run created after
     * the operator locked the period won't have seen that event — so HR
     * calls this to catch up. If attendance hasn't locked the period, we
     * refuse and tell HR to close it in attendance first rather than
     * computing against a still-open month. Idempotent against re-runs
     * via the DRAFT precondition; re-locking an already-LOCKED run is a
     * 409 (nothing to do).
     */
    @Transactional
    public PayrollRunDto lockAttendance(long id) {
        PayrollRun r = load(id);
        if (r.getStatus() != PayrollRunStatus.DRAFT) {
            throw new ConflictError(PayrollErrorCode.PAYROLL_RUN_INVALID_STATE,
                    "lock-attendance requires DRAFT (current: " + r.getStatus() + ")");
        }
        if (!attendance.isPeriodLocked(r.getPeriodYear(), r.getPeriodMonth())) {
            throw new ConflictError(PayrollErrorCode.ATTENDANCE_PERIOD_NOT_LOCKED,
                    "attendance period %d-%02d is not locked — lock it from attendance before payroll"
                            .formatted(r.getPeriodYear(), r.getPeriodMonth()));
        }
        r.setStatus(PayrollRunStatus.LOCKED);
        r.setAttendanceLocked(true);
        return PayrollRunDtoMapper.of(r);
    }

    /**
     * Synchronous kickoff for compute. Flips {@code LOCKED → COMPUTING},
     * snapshots the candidate count + caller's identity, and registers a
     * {@link PayrollRunComputeSaga} that does the per-employee work on
     * the runner thread. Returns immediately so a 5,000-employee run
     * doesn't time the FE out; HR polls {@code GET /payroll/runs/{id}}
     * for {@code progressCount} / {@code totalCount} (or
     * {@code GET /sagas/?name=PAYROLL_RUN_COMPUTE&correlationKey=…} for
     * step-level state).
     *
     * <p>Re-running on a run already in {@code COMPUTING} is a no-op —
     * the unique {@code (saga_name, correlation_key)} index returns the
     * existing saga row (idempotent). Re-running on {@code COMPUTED}
     * throws — caller must {@code /reset} first.
     */
    @Transactional
    public PayrollRunDto compute(long id) {
        PayrollRun r = load(id);
        if (r.getStatus() == PayrollRunStatus.COMPUTING) {
            startComputeSaga(r);   // idempotent — returns the existing saga row
            return PayrollRunDtoMapper.of(r);
        }
        if (r.getStatus() != PayrollRunStatus.LOCKED) {
            throw new ConflictError(PayrollErrorCode.PAYROLL_RUN_INVALID_STATE,
                    "compute requires LOCKED (current: " + r.getStatus() + ")");
        }
        r.setStatus(PayrollRunStatus.COMPUTING);
        r.setProgressCount(0);
        r.setTotalCount((int) Math.min(Integer.MAX_VALUE,
                em.createQuery("select count(s) from EmployeePayrollSettings s", Long.class)
                        .getSingleResult()));
        startComputeSaga(r);
        return PayrollRunDtoMapper.of(r);
    }

    @Transactional(readOnly = true)
    public PageResponse<PayrollRunSkipDto> listSkips(long runId, Pageable pageable) {
        load(runId);
        return PageResponse.from(skips.findByPayrollRunId(runId, pageable).map(PayrollRunSkipDtoMapper::of));
    }

    /**
     * Re-run compute for the given (or all) skipped employees on a
     * COMPUTED run. Skip rows for employees whose retry produced a
     * payslip are deleted; rows whose retry still failed are updated
     * with the new error if changed; the run's
     * {@link PayrollRun#getSkippedCount()} is rewritten from the
     * surviving rows. Status stays COMPUTED. Re-running with the same
     * payload after success is a no-op because the skip rows are gone.
     */
    @Transactional
    public PayrollRunDto retrySkips(long id, List<Long> employeeIds) {
        PayrollRun r = load(id);
        if (r.getStatus() != PayrollRunStatus.COMPUTED) {
            throw new ConflictError(PayrollErrorCode.PAYROLL_RUN_INVALID_STATE,
                    "retry-skips requires COMPUTED (current: " + r.getStatus() + ")");
        }
        List<com.forward.hrms.payroll.entity.PayrollRunSkip> targets =
                (employeeIds == null || employeeIds.isEmpty())
                        ? skips.findAllByPayrollRunId(id)
                        : skips.findByPayrollRunIdAndEmployeeIdIn(id, employeeIds);
        if (targets.isEmpty()) {
            return PayrollRunDtoMapper.of(r);
        }
        List<Long> targetIds = targets.stream()
                .map(com.forward.hrms.payroll.entity.PayrollRunSkip::getEmployeeId)
                .toList();

        PayrollComputeService.RetryOutcomes outcomes = compute.recomputeFor(r, targetIds);

        if (!outcomes.succeededEmployeeIds().isEmpty()) {
            skips.deleteByPayrollRunIdAndEmployeeIdIn(id, outcomes.succeededEmployeeIds());
        }
        // Update existing rows where the failure changed (different code or
        // message) — HR sees the new actionable reason without us shuffling
        // ids. Rows that didn't change stay untouched.
        Map<Long, com.forward.hrms.payroll.entity.PayrollRunSkip> byId =
                targets.stream().collect(java.util.stream.Collectors.toMap(
                        com.forward.hrms.payroll.entity.PayrollRunSkip::getEmployeeId, s -> s));
        for (PayrollComputeService.SkippedEmployee s : outcomes.stillFailed()) {
            com.forward.hrms.payroll.entity.PayrollRunSkip existing = byId.get(s.employeeId());
            if (existing == null) continue;
            if (!java.util.Objects.equals(existing.getErrorCode(), s.errorCode())
                    || !java.util.Objects.equals(existing.getMessage(), s.message())) {
                existing.setErrorCode(s.errorCode());
                existing.setMessage(s.message().length() <= 1000 ? s.message() : s.message().substring(0, 1000));
            }
        }
        int remaining = skips.countByPayrollRunId(id);
        r.setSkippedCount(remaining);

        Map<String, Object> auditAfter = new java.util.LinkedHashMap<>();
        auditAfter.put("requested", targetIds.size());
        auditAfter.put("resolved", outcomes.succeededEmployeeIds().size());
        auditAfter.put("stillSkipped", remaining);
        if (!outcomes.stillFailed().isEmpty()) {
            auditAfter.put("skipped", skipSampleForAudit(outcomes.stillFailed()));
        }
        audit.emit("payroll_run", String.valueOf(id), "run_skips_retried", null, auditAfter, true);
        return PayrollRunDtoMapper.of(r);
    }

    private static List<Map<String, Object>> skipSampleForAudit(
            List<PayrollComputeService.SkippedEmployee> all) {
        // Audit sampling for the synchronous retry-skips path; the async
        // compute saga has its own copy in PayrollComputeService because
        // that's where the run.computed audit emit now lives.
        int max = Math.min(20, all.size());
        List<Map<String, Object>> sample = new java.util.ArrayList<>(max);
        for (int i = 0; i < max; i++) {
            PayrollComputeService.SkippedEmployee s = all.get(i);
            sample.add(Map.of("employeeId", s.employeeId(),
                    "errorCode", s.errorCode(),
                    "message", s.message()));
        }
        return sample;
    }

    /**
     * Reset a COMPUTED run back to LOCKED. Drops every payslip on the
     * run (payslip_lines cascade at the schema level), clears
     * {@code computed_at}, and emits {@code payroll.run.reset} so any
     * lifecycle consumers can invalidate their cached numbers. The
     * attendance period stays locked — only the payroll-side compute
     * artifacts are wiped, so the operator can fix employee data and
     * recompute without re-running the lock-attendance step.
     */
    @Transactional
    public PayrollRunDto reset(long id) {
        PayrollRun r = load(id);
        if (r.getStatus() != PayrollRunStatus.COMPUTED) {
            throw new ConflictError(PayrollErrorCode.PAYROLL_RUN_INVALID_STATE,
                    "reset requires COMPUTED (current: " + r.getStatus() + ")");
        }
        // Compute's 500-note rounding wrote one "From Salary" RFD row per cash
        // payslip, dated the payslip's created date. Matched through the
        // payslips, so this has to run before they are deleted.
        int deletedRfd = rfdAdvances.deleteCreatedByRun(id);
        int deleted = payslips.deleteAllByPayrollRunId(id);
        int deletedSkips = skips.deleteAllByPayrollRunId(id);
        // Compute wrote a permanent-advance ledger row per payslip; without
        // this the recompute would see them as already deducted.
        permanentAdvanceDeductions.deleteBySource(com.forward.hrms.payroll.enums.PermanentAdvanceDeductionSource.SALARY, id);
        // Drop the prior PAYROLL_RUN_COMPUTE saga row so the next /compute
        // gets a fresh row. SagaService.start is keyed by
        // (sagaName, correlationKey) and returns terminal-state rows
        // unchanged — without this, the run flips to COMPUTING but no
        // saga advances it and the run hangs.
        int droppedSaga = sagaService.clear(PayrollRunComputeSaga.NAME, "payroll_run:" + id);
        r.setStatus(PayrollRunStatus.LOCKED);
        r.setComputedAt(null);
        r.setSkippedCount(0);
        r.setProgressCount(0);
        r.setTotalCount(0);

        String by = CurrentUserHolder.get().map(CurrentUser::username).orElse("system");
        outbox.publish(PayrollEvents.TOPIC, "payroll_run:" + id,
                PayrollEvents.RUN_RESET,
                new PayrollEvents.RunReset(id, r.getFiscalYearCode(), deleted, by));
        audit.emit("payroll_run", String.valueOf(id), "run_reset", null,
                Map.of("deletedPayslips", deleted, "deletedSkips", deletedSkips, "deletedRfdAdvances", deletedRfd,
                        "droppedSaga", droppedSaga, "by", by), true);
        return PayrollRunDtoMapper.of(r);
    }

    @Transactional
    public PayrollRunDto approve(long id) {
        PayrollRun r = load(id);
        if (r.getStatus() != PayrollRunStatus.COMPUTED) {
            throw new ConflictError(PayrollErrorCode.PAYROLL_RUN_INVALID_STATE,
                    "approve requires COMPUTED");
        }
        r.setStatus(PayrollRunStatus.APPROVED);
        r.setApprovedAt(LocalDateTime.now());

        outbox.publish(PayrollEvents.TOPIC, "payroll_run:" + id,
                PayrollEvents.RUN_APPROVED,
                new PayrollEvents.RunApproved(id, r.getFiscalYearCode()));
        audit.emit("payroll_run", String.valueOf(id), "run_approved", null,
                Map.of("approvedAt", r.getApprovedAt().toString()), true);
        return PayrollRunDtoMapper.of(r);
    }

    /**
     * Synchronous kickoff for the post step. Flips APPROVED → POSTING and
     * registers a saga that does the real work (PDF generation, carryover
     * release, run.posted emission) on the runner thread. Returns
     * immediately; HR polls the saga state via {@code GET /sagas/{id}}.
     *
     * <p>Re-running on a run already in POSTING is a no-op — the unique
     * {@code (saga_name, correlation_key)} index returns the existing
     * saga row.
     */
    @Transactional
    public PayrollRunDto post(long id) {
        PayrollRun r = load(id);
        if (r.getStatus() == PayrollRunStatus.POSTING || r.getStatus() == PayrollRunStatus.POSTED) {
            startPostSaga(r);   // idempotent — returns the existing row
            return PayrollRunDtoMapper.of(r);
        }
        if (r.getStatus() != PayrollRunStatus.APPROVED) {
            throw new ConflictError(PayrollErrorCode.PAYROLL_RUN_INVALID_STATE,
                    "post requires APPROVED");
        }
        r.setStatus(PayrollRunStatus.POSTING);
        // Stale compute counters would otherwise show on the FE for a
        // second or two before the post-saga step takes over; reset here
        // so the spinner says "Generating PDFs 0/0" until generateAll
        // fires its first pulse.
        r.setProgressCount(0);
        r.setTotalCount(0);
        startPostSaga(r);
        return PayrollRunDtoMapper.of(r);
    }

    /**
     * Event-driven counterpart to {@link #lockAttendance}: the operator
     * locked the period from the attendance side ({@code
     * attendance.period_locked}) and payroll catches up. Flips a matching
     * {@code DRAFT} run to {@code LOCKED} with {@code attendance_locked}
     * set — the same outcome as the synchronous lock-attendance call, so
     * the two paths converge idempotently (operator-first or payroll-first
     * both land here / there). No matching run, or a run already past
     * {@code DRAFT}, is a no-op: re-delivery and the payroll-initiated path
     * (which sets LOCKED itself) leave nothing to do.
     */
    @Transactional
    public void onAttendancePeriodLocked(int year, int month) {
        runs.findByPeriodYearAndPeriodMonth(year, month).ifPresent(r -> {
            if (r.getStatus() == PayrollRunStatus.DRAFT) {
                r.setStatus(PayrollRunStatus.LOCKED);
                r.setAttendanceLocked(true);
            }
        });
    }

    @Transactional
    public PayrollRunDto cancel(long id) {
        PayrollRun r = load(id);
        if (r.getStatus() != PayrollRunStatus.DRAFT && r.getStatus() != PayrollRunStatus.LOCKED) {
            throw new ConflictError(PayrollErrorCode.PAYROLL_RUN_INVALID_STATE,
                    "cancel allowed only on DRAFT or LOCKED runs");
        }
        r.setStatus(PayrollRunStatus.CANCELLED);
        return PayrollRunDtoMapper.of(r);
    }

    private void startPostSaga(PayrollRun r) {
        ObjectNode payload = sagaJson.createObjectNode();
        payload.put("runId", r.getId());
        payload.put("fiscalYearCode", r.getFiscalYearCode());
        payload.put("year", r.getPeriodYear());
        payload.put("month", r.getPeriodMonth());
        // Snapshot the kickoff caller's X-User-* headers so the GeneratePdfs
        // saga step (running on a non-request thread) can reach
        // employee-service /employees/by-ids without 403.
        SagaContext.writeIdentity(payload, EmployeeClient.currentRequestIdentity());
        sagaService.start(PayrollRunPostSaga.NAME, "payroll_run:" + r.getId(), payload);
    }

    private void startComputeSaga(PayrollRun r) {
        ObjectNode payload = sagaJson.createObjectNode();
        payload.put("runId", r.getId());
        payload.put("fiscalYearCode", r.getFiscalYearCode());
        SagaContext.writeIdentity(payload, EmployeeClient.currentRequestIdentity());
        sagaService.start(PayrollRunComputeSaga.NAME, "payroll_run:" + r.getId(), payload);
    }

    PayrollRun load(long id) {
        return runs.findById(id).orElseThrow(
                () -> new NotFoundError(PayrollErrorCode.PAYROLL_RUN_NOT_FOUND, "payroll run not found"));
    }
}
