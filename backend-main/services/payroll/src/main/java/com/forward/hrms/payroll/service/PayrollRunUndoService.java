package com.forward.hrms.payroll.service;

import com.forward.hrms.commons.cache.EmployeeDisplay;
import com.forward.hrms.commons.cache.EmployeeDisplayCache;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.core.error.UnprocessableError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.payroll.dto.response.PayrollRunDto;
import com.forward.hrms.payroll.dto.response.PayrollRunDtoMapper;
import com.forward.hrms.payroll.dto.response.PayslipDto;
import com.forward.hrms.payroll.dto.response.RunSkipAdvanceDto;
import com.forward.hrms.payroll.entity.PayrollRun;
import com.forward.hrms.payroll.entity.PayrollRunSkip;
import com.forward.hrms.payroll.entity.Payslip;
import com.forward.hrms.payroll.entity.PermanentAdvance;
import com.forward.hrms.payroll.entity.PermanentAdvanceWaiver;
import com.forward.hrms.payroll.entity.RfdAdvance;
import com.forward.hrms.payroll.enums.AdvanceStatus;
import com.forward.hrms.payroll.enums.PayrollErrorCode;
import com.forward.hrms.payroll.enums.PayrollRunStatus;
import com.forward.hrms.payroll.enums.PayslipStatus;
import com.forward.hrms.payroll.enums.PermanentAdvanceDeductionSource;
import com.forward.hrms.payroll.repository.PayrollRunRepository;
import com.forward.hrms.payroll.repository.PayrollRunSkipRepository;
import com.forward.hrms.payroll.repository.PayslipRepository;
import com.forward.hrms.payroll.repository.PermanentAdvanceDeductionRepository;
import com.forward.hrms.payroll.repository.PermanentAdvanceRepository;
import com.forward.hrms.payroll.repository.PermanentAdvanceWaiverRepository;
import com.forward.hrms.payroll.repository.RfdAdvanceRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Per-employee counterpart to {@link PayrollRunService#reset}: undo one
 * payslip on a COMPUTED run and park the employee on the run's skip list,
 * where HR fixes their data and brings them back with
 * {@code POST /retry-skips}. Before that retry HR can preview the
 * employee's payslip and waive their permanent-advance installment for
 * the run's month.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayrollRunUndoService {

    /** Skip-row error code for a payslip HR undid by hand. */
    public static final String MANUALLY_UNDONE = "MANUALLY_UNDONE";

    private final PayrollRunRepository runs;
    private final PayslipRepository payslips;
    private final PayrollRunSkipRepository skips;
    private final RfdAdvanceRepository rfdAdvances;
    private final PermanentAdvanceRepository permanentAdvances;
    private final PermanentAdvanceDeductionRepository permanentAdvanceDeductions;
    private final PermanentAdvanceWaiverRepository waivers;
    private final PayrollComputeService compute;
    private final EmployeeDisplayCache displayCache;
    private final AuditEmitter audit;

    /**
     * Drop one DRAFT payslip from a COMPUTED run and add the employee to the
     * skip list. Removes everything that payslip's compute wrote — its
     * "From Salary" RFD row, its permanent-advance ledger row and the
     * payslip itself (lines cascade) — so the retry starts clean and does
     * not see the installment as already deducted. HELD payslips must be
     * unheld first.
     */
    @Transactional
    public PayrollRunDto undoPayslip(long runId, long payslipId) {
        PayrollRun run = requireComputed(runId, "undo");
        Payslip ps = payslips.findById(payslipId)
                .filter(p -> p.getPayrollRunId() == runId)
                .orElseThrow(() -> new NotFoundError(PayrollErrorCode.PAYSLIP_NOT_FOUND,
                        "payslip " + payslipId + " is not on run " + runId));
        if (ps.getStatus() != PayslipStatus.DRAFT) {
            throw new ConflictError(PayrollErrorCode.PAYSLIP_INVALID_STATE,
                    "only a DRAFT payslip can be undone (current: " + ps.getStatus() + ")");
        }
        long employeeId = ps.getEmployeeId();

        LocalDate periodEnd = YearMonth.of(run.getPeriodYear(), run.getPeriodMonth()).atEndOfMonth();
        int deletedRfd = rfdAdvances.deleteFromSalary(employeeId, ps.getCreatedAt().toLocalDate(),
                RfdAdvance.FROM_SALARY
                        + periodEnd.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                        + " " + periodEnd.getYear());
        int deletedDeductions = permanentAdvanceDeductions.deleteBySourceForEmployee(
                PermanentAdvanceDeductionSource.SALARY, runId, employeeId);
        payslips.delete(ps);

        String by = CurrentUserHolder.usernameOrSystem();
        PayrollRunSkip skip = new PayrollRunSkip();
        skip.setPayrollRunId(runId);
        skip.setEmployeeId(employeeId);
        skip.setEmployeeCode(ps.getEmployeeCode());
        skip.setEmployeeFullName(fullNameOr(employeeId, ps.getEmployeeCode()));
        skip.setErrorCode(MANUALLY_UNDONE);
        skip.setMessage("Payslip undone by " + by + ". Fix the data, then retry.");
        skip.setCreatedAt(LocalDateTime.now());
        skips.save(skip);
        run.setSkippedCount(skips.countByPayrollRunId(runId));

        Map<String, Object> before = new LinkedHashMap<>();
        before.put("payslipId", payslipId);
        before.put("employeeId", employeeId);
        before.put("net", ps.getNet());
        audit.emit("payroll_run", String.valueOf(runId), "payslip_undone", before,
                Map.of("employeeId", employeeId,
                        "deletedRfdAdvances", deletedRfd,
                        "deletedPermanentAdvanceDeductions", deletedDeductions,
                        "by", by),
                true);
        return PayrollRunDtoMapper.of(run);
    }

    /** The skipped employee's running permanent advance, as the next compute will see it. */
    @Transactional(readOnly = true)
    public RunSkipAdvanceDto skipAdvance(long runId, long employeeId) {
        PayrollRun run = load(runId);
        requireSkipped(runId, employeeId);
        return advanceOf(run, employeeId);
    }

    /**
     * Waive (or restore) the skipped employee's permanent-advance installment
     * for this run's month. The advance's {@code monthly_deduction} is left
     * alone: the retry deducts 0 and writes a 0 ledger row, and the next
     * month deducts as usual.
     */
    @Transactional
    public RunSkipAdvanceDto setSkipAdvanceWaiver(long runId, long employeeId, boolean waived) {
        PayrollRun run = requireComputed(runId, "waive the advance installment");
        requireSkipped(runId, employeeId);
        RunSkipAdvanceDto current = advanceOf(run, employeeId);
        if (current.waivedThisMonth() == waived) return current;
        if (waived) {
            if (current.advanceId() == null) {
                throw new UnprocessableError(PayrollErrorCode.PERMANENT_ADVANCE_NOT_FOUND,
                        "employee " + employeeId + " has no running permanent advance");
            }
            PermanentAdvanceWaiver w = new PermanentAdvanceWaiver();
            w.setEmployeeId(employeeId);
            w.setPeriodYear(run.getPeriodYear());
            w.setPeriodMonth(run.getPeriodMonth());
            waivers.save(w);
        } else {
            waivers.deleteForPeriod(employeeId, run.getPeriodYear(), run.getPeriodMonth());
        }

        audit.emit("permanent_advance_waiver", employeeId + ":" + run.getPeriodYear() + "-" + run.getPeriodMonth(),
                waived ? "installment_waived" : "installment_restored",
                Map.of("waived", !waived),
                Map.of("waived", waived, "payrollRunId", runId),
                true);
        return new RunSkipAdvanceDto(employeeId, current.advanceId(), current.requestCode(),
                current.taken(), current.deducted(), current.outstanding(), current.monthlyDeduction(), waived);
    }

    /**
     * The payslip {@code /retry-skips} would compute for this employee right
     * now (waiver included), rolled back instead of saved.
     */
    public PayslipDto previewSkip(long runId, long employeeId) {
        PayrollRun run = load(runId);
        requireSkipped(runId, employeeId);
        return compute.previewFor(run, employeeId);
    }

    /** Mirrors {@code PayrollComputeService.applyPermanentAdvance}'s reads. */
    private RunSkipAdvanceDto advanceOf(PayrollRun run, long employeeId) {
        BigDecimal taken = orZero(permanentAdvances.employeeTotalPermanentAdvance(employeeId));
        BigDecimal deducted = orZero(permanentAdvanceDeductions.employeeTotalPermanentAdvanceDeduction(employeeId));
        BigDecimal outstanding = taken.subtract(deducted);
        boolean waived = waivers.existsByEmployeeIdAndPeriodYearAndPeriodMonth(
                employeeId, run.getPeriodYear(), run.getPeriodMonth());
        PermanentAdvance latest = outstanding.signum() > 0
                ? permanentAdvances.findTopByEmployeeIdAndStatusOrderByIdDesc(employeeId, AdvanceStatus.APPROVED)
                : null;
        if (latest == null) {
            return new RunSkipAdvanceDto(employeeId, null, null, taken, deducted,
                    outstanding.max(BigDecimal.ZERO), null, waived);
        }
        return new RunSkipAdvanceDto(employeeId, latest.getId(), latest.getRequestCode(),
                taken, deducted, outstanding, latest.getMonthlyDeduction(), waived);
    }

    /** Display name for the skip row; the code stands in if employee lookup is down — not worth failing the undo. */
    private String fullNameOr(long employeeId, String fallback) {
        try {
            EmployeeDisplay d = displayCache.getMany(List.of(employeeId)).get(employeeId);
            if (d != null && d.fullName() != null) return d.fullName();
        } catch (RuntimeException ex) {
            log.warn("undo: display lookup failed for employee {}: {}", employeeId, ex.getMessage());
        }
        return fallback;
    }

    private static BigDecimal orZero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private void requireSkipped(long runId, long employeeId) {
        if (!skips.existsByPayrollRunIdAndEmployeeId(runId, employeeId)) {
            throw new NotFoundError(PayrollErrorCode.PAYROLL_RUN_SKIP_NOT_FOUND,
                    "employee " + employeeId + " is not skipped on run " + runId);
        }
    }

    private PayrollRun requireComputed(long runId, String action) {
        PayrollRun run = load(runId);
        if (run.getStatus() != PayrollRunStatus.COMPUTED) {
            throw new ConflictError(PayrollErrorCode.PAYROLL_RUN_INVALID_STATE,
                    action + " requires COMPUTED (current: " + run.getStatus() + ")");
        }
        return run;
    }

    private PayrollRun load(long runId) {
        return runs.findById(runId).orElseThrow(
                () -> new NotFoundError(PayrollErrorCode.PAYROLL_RUN_NOT_FOUND, "payroll run not found"));
    }
}
