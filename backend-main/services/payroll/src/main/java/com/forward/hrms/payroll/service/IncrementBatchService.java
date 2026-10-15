package com.forward.hrms.payroll.service;

import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.kafka.outbox.Outbox;
import com.forward.hrms.payroll.dto.request.CreateIncrementBatchLineRequest;
import com.forward.hrms.payroll.dto.request.CreateIncrementBatchRequest;
import com.forward.hrms.payroll.dto.request.UpdateIncrementBatchLineRequest;
import com.forward.hrms.payroll.dto.request.UpdateIncrementBatchRequest;
import com.forward.hrms.payroll.dto.response.IncrementBatchDto;
import com.forward.hrms.payroll.dto.response.IncrementBatchLineDto;
import com.forward.hrms.payroll.entity.EmployeeSalary;
import com.forward.hrms.payroll.entity.EmployeeSalaryComponent;
import com.forward.hrms.payroll.entity.IncrementBatch;
import com.forward.hrms.payroll.entity.IncrementBatchLine;
import com.forward.hrms.payroll.enums.IncrementBatchLineStatus;
import com.forward.hrms.payroll.enums.IncrementBatchStatus;
import com.forward.hrms.payroll.enums.PayrollErrorCode;
import com.forward.hrms.payroll.events.PayrollEvents;
import com.forward.hrms.payroll.repository.EmployeeSalaryComponentRepository;
import com.forward.hrms.payroll.repository.EmployeeSalaryRepository;
import com.forward.hrms.payroll.repository.IncrementBatchLineRepository;
import com.forward.hrms.payroll.repository.IncrementBatchRepository;
import com.forward.hrms.payroll.repository.PayrollRunRepository;
import com.forward.hrms.payroll.dto.response.IncrementBatchDtoMapper;
import com.forward.hrms.payroll.dto.response.IncrementBatchLineDtoMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class IncrementBatchService {

    private final IncrementBatchRepository batches;
    private final IncrementBatchLineRepository lines;
    private final EmployeeSalaryRepository salaries;
    private final EmployeeSalaryComponentRepository salaryComponents;
    private final PayrollRunRepository runs;
    private final EmployeeSalaryService salaryService;
    private final EmployeeSalaryComponentService componentService;
    private final FiscalYearService fiscalYears;
    private final Outbox outbox;
    private final AuditEmitter audit;
    private final com.forward.hrms.commons.batch.BatchRunner batch;

    @Transactional(readOnly = true)
    public List<IncrementBatchDto> list(IncrementBatchStatus status, String fiscalYearCode) {
        LocalDate fyStart = null;
        LocalDate fyEnd = null;
        if (fiscalYearCode != null) {
            var fy = fiscalYears.list().stream()
                    .filter(f -> f.code().equals(fiscalYearCode)).findFirst().orElseThrow(
                            () -> new NotFoundError(PayrollErrorCode.FISCAL_YEAR_NOT_FOUND,
                                    "fiscal year " + fiscalYearCode + " not found"));
            fyStart = fy.startDate();
            fyEnd = fy.endDate();
        }
        return batches.filter(status, fyStart, fyEnd).stream().map(IncrementBatchDtoMapper::of).toList();
    }

    @Transactional(readOnly = true)
    public IncrementBatchDto get(long id) {
        IncrementBatch b = load(id);
        List<IncrementBatchLineDto> lineDtos = lines.findAllByBatchIdOrderByEmployeeId(id).stream()
                .map(IncrementBatchLineDtoMapper::of).toList();
        return IncrementBatchDtoMapper.of(b, lineDtos);
    }

    @Transactional
    public IncrementBatchDto create(CreateIncrementBatchRequest req) {
        IncrementBatch b = new IncrementBatch();
        b.setName(req.name());
        b.setEffectiveFrom(req.effectiveFrom());
        b.setStatus(IncrementBatchStatus.DRAFT);
        b.setNotes(req.notes());
        b = batches.save(b);

        for (CreateIncrementBatchRequest.Line input : req.lines()) {
            LocalDate effFrom = input.effectiveFrom() == null ? req.effectiveFrom() : input.effectiveFrom();
            IncrementBatchLine l = new IncrementBatchLine();
            l.setBatchId(b.getId());
            l.setEmployeeId(input.employeeId());
            l.setNewBasic(input.newBasic());
            l.setNewIncomeTax(input.newIncomeTax());
            l.setEffectiveFrom(effFrom);
            validateAndSnapshot(l);
            lines.save(l);
        }
        return get(b.getId());
    }

    @Transactional
    public IncrementBatchDto update(long id, UpdateIncrementBatchRequest req) {
        IncrementBatch b = load(id);
        ensureDraft(b);
        if (req.name() != null) b.setName(req.name());
        if (req.effectiveFrom() != null) b.setEffectiveFrom(req.effectiveFrom());
        if (req.notes() != null) b.setNotes(req.notes());
        return get(id);
    }

    @Transactional
    public IncrementBatchLineDto addLine(long batchId, CreateIncrementBatchLineRequest req) {
        IncrementBatch b = load(batchId);
        ensureDraft(b);
        if (lines.findByBatchIdAndEmployeeId(batchId, req.employeeId()).isPresent()) {
            throw new ConflictError(PayrollErrorCode.INCREMENT_BATCH_INVALID_STATE,
                    "batch already has a line for employee " + req.employeeId());
        }
        LocalDate effFrom = req.effectiveFrom() == null ? b.getEffectiveFrom() : req.effectiveFrom();
        IncrementBatchLine l = new IncrementBatchLine();
        l.setBatchId(batchId);
        l.setEmployeeId(req.employeeId());
        l.setNewBasic(req.newBasic());
        l.setNewIncomeTax(req.newIncomeTax());
        l.setEffectiveFrom(effFrom);
        validateAndSnapshot(l);
        lines.save(l);
        return IncrementBatchLineDtoMapper.of(l);
    }

    @Transactional
    public IncrementBatchLineDto updateLine(long batchId, long lineId,
                                            UpdateIncrementBatchLineRequest req) {
        IncrementBatch b = load(batchId);
        ensureDraft(b);
        IncrementBatchLine l = lines.findById(lineId).orElseThrow(
                () -> new NotFoundError(PayrollErrorCode.INCREMENT_LINE_NOT_FOUND, "line not found"));
        if (!l.getBatchId().equals(batchId)) {
            throw new NotFoundError(PayrollErrorCode.INCREMENT_LINE_NOT_FOUND, "line not found");
        }
        if (req.newBasic() != null) l.setNewBasic(req.newBasic());
        if (req.newIncomeTax() != null) l.setNewIncomeTax(req.newIncomeTax());
        if (req.effectiveFrom() != null) l.setEffectiveFrom(req.effectiveFrom());
        validateAndSnapshot(l);
        return IncrementBatchLineDtoMapper.of(l);
    }

    @Transactional
    public void deleteLine(long batchId, long lineId) {
        IncrementBatch b = load(batchId);
        ensureDraft(b);
        IncrementBatchLine l = lines.findById(lineId).orElseThrow(
                () -> new NotFoundError(PayrollErrorCode.INCREMENT_LINE_NOT_FOUND, "line not found"));
        if (!l.getBatchId().equals(batchId)) {
            throw new NotFoundError(PayrollErrorCode.INCREMENT_LINE_NOT_FOUND, "line not found");
        }
        lines.delete(l);
    }

    @Transactional
    public IncrementBatchDto apply(long id) {
        IncrementBatch b = load(id);
        if (b.getStatus() == IncrementBatchStatus.APPLIED) return get(id);
        ensureDraft(b);
        long errors = lines.countByBatchIdAndStatus(id, IncrementBatchLineStatus.ERROR);
        if (errors > 0) {
            throw new ConflictError(PayrollErrorCode.INCREMENT_BATCH_HAS_ERRORS,
                    errors + " line(s) in ERROR state — fix or delete before applying");
        }
        List<IncrementBatchLine> all = lines.findAllByBatchIdOrderByEmployeeId(id);
        // Re-validate each line to catch state drift between preview and apply.
        // (validateAndSnapshot writes to the line — keep this in the outer tx
        // so a re-validation hit lands before we flip the batch status.)
        for (IncrementBatchLine l : all) {
            validateAndSnapshot(l);
            if (l.getStatus() == IncrementBatchLineStatus.ERROR) {
                throw new ConflictError(PayrollErrorCode.INCREMENT_BATCH_HAS_ERRORS,
                        "line for employee " + l.getEmployeeId() + " no longer valid: "
                                + l.getErrorMessage());
            }
        }
        // §7.payroll #32 — per-line REQUIRES_NEW so a single bad row
        // (e.g. salary record vanished mid-apply) doesn't undo the
        // already-applied increments. Lines that fail are surfaced
        // through the audit row + ERROR status so HR can fix and rerun.
        com.forward.hrms.commons.batch.BatchOutcomes<IncrementBatchLine, Long> outcomes =
                batch.runWithPartial(all, line -> {
                    LocalDate effFrom = line.getEffectiveFrom() == null ? b.getEffectiveFrom() : line.getEffectiveFrom();
                    salaryService.reviseInternal(line.getEmployeeId(), line.getNewBasic(), effFrom,
                            "increment batch: " + b.getName(), false);
                    if (line.getNewIncomeTax() != null) {
                        componentService.replaceIncomeTax(line.getEmployeeId(), line.getNewIncomeTax(), effFrom);
                    }
                    line.setStatus(IncrementBatchLineStatus.APPLIED);
                    return line.getEmployeeId();
                });

        int applied = outcomes.succeededCount();
        // Flip failing lines to ERROR + capture reason on the line so HR
        // sees per-row failure in the same screen. Done OUTSIDE the
        // per-line tx because each per-line tx rolled back; we have to
        // write the ERROR status here in the outer apply() tx.
        for (var f : outcomes.failed()) {
            IncrementBatchLine line = f.item();
            // Re-fetch since the per-line tx rolled back and the entity is detached.
            IncrementBatchLine fresh = lines.findById(line.getId()).orElse(null);
            if (fresh == null) continue;
            fresh.setStatus(IncrementBatchLineStatus.ERROR);
            fresh.setErrorMessage("apply failed: " + f.errorCode() + " — " + f.message());
        }

        boolean fullyApplied = outcomes.failed().isEmpty();
        b.setStatus(fullyApplied ? IncrementBatchStatus.APPLIED : IncrementBatchStatus.PARTIALLY_APPLIED);
        b.setAppliedAt(LocalDateTime.now());

        outbox.publish(PayrollEvents.TOPIC, "increment_batch:" + id,
                PayrollEvents.INCREMENT_BATCH_APPLIED,
                new PayrollEvents.IncrementBatchApplied(id, b.getName(), b.getEffectiveFrom(),
                        applied, CurrentUserHolder.usernameOrSystem()));
        Map<String, Object> auditAfter = new java.util.LinkedHashMap<>();
        auditAfter.put("name", b.getName());
        auditAfter.put("employeeCount", applied);
        if (!outcomes.failed().isEmpty()) {
            auditAfter.put("failedCount", outcomes.failedCount());
            List<Map<String, Object>> sample = new java.util.ArrayList<>();
            int max = Math.min(20, outcomes.failed().size());
            for (int i = 0; i < max; i++) {
                var f = outcomes.failed().get(i);
                sample.add(Map.of("employeeId", f.item().getEmployeeId(),
                        "errorCode", f.errorCode(),
                        "message", f.message()));
            }
            auditAfter.put("failed", sample);
        }
        audit.emit("increment_batch", String.valueOf(id), "increment_batch_applied",
                null, auditAfter, true);
        return get(id);
    }

    @Transactional
    public IncrementBatchDto cancel(long id) {
        IncrementBatch b = load(id);
        ensureDraft(b);
        b.setStatus(IncrementBatchStatus.CANCELLED);
        return get(id);
    }

    private void validateAndSnapshot(IncrementBatchLine l) {
        List<String> problems = new ArrayList<>();
        EmployeeSalary current = salaries.findCurrent(l.getEmployeeId()).orElse(null);
        if (current == null) {
            problems.add("employee has no current basic salary");
            l.setOldBasic(java.math.BigDecimal.ZERO);
        } else {
            l.setOldBasic(current.getBasic());
        }
        EmployeeSalaryComponent currentTax = salaryComponents
                .findCurrentByCode(l.getEmployeeId(), "INCOME_TAX").orElse(null);
        l.setOldIncomeTax(currentTax == null ? null : currentTax.getAmount());

        if (l.getNewIncomeTax() != null && currentTax == null) {
            problems.add("newIncomeTax provided but employee has no INCOME_TAX line");
        }

        LocalDate effFrom = l.getEffectiveFrom();
        if (effFrom != null) {
            int year = effFrom.getYear();
            int month = effFrom.getMonthValue();
            runs.findByPeriodYearAndPeriodMonth(year, month).ifPresent(run -> {
                // §7.payroll #33 — POSTED was the original guard, but
                // COMPUTING / COMPUTED / APPROVED / POSTING runs already
                // hold finished payslips that won't reflect the new basic.
                // Block all in-flight states so the increment doesn't
                // silently miss the run it should've affected.
                var status = run.getStatus();
                if (status == com.forward.hrms.payroll.enums.PayrollRunStatus.COMPUTING
                        || status == com.forward.hrms.payroll.enums.PayrollRunStatus.COMPUTED
                        || status == com.forward.hrms.payroll.enums.PayrollRunStatus.APPROVED
                        || status == com.forward.hrms.payroll.enums.PayrollRunStatus.POSTING
                        || status == com.forward.hrms.payroll.enums.PayrollRunStatus.POSTED) {
                    problems.add("effectiveFrom falls inside an in-flight run ("
                            + status + ") for " + year + "-" + String.format("%02d", month));
                }
            });
        }

        if (problems.isEmpty()) {
            l.setStatus(IncrementBatchLineStatus.VALID);
            l.setErrorMessage(null);
        } else {
            l.setStatus(IncrementBatchLineStatus.ERROR);
            l.setErrorMessage(String.join("; ", problems));
        }
    }

    private void ensureDraft(IncrementBatch b) {
        if (b.getStatus() != IncrementBatchStatus.DRAFT) {
            throw new ConflictError(PayrollErrorCode.INCREMENT_BATCH_INVALID_STATE,
                    "batch is " + b.getStatus() + " — only DRAFT batches can be modified");
        }
    }

    IncrementBatch load(long id) {
        return batches.findById(id).orElseThrow(
                () -> new NotFoundError(PayrollErrorCode.INCREMENT_BATCH_NOT_FOUND,
                        "increment batch not found"));
    }
}
