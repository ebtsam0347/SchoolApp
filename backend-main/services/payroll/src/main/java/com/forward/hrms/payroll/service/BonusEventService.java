package com.forward.hrms.payroll.service;

import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.core.money.Money;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.kafka.outbox.Outbox;
import com.forward.hrms.payroll.dto.request.ApplyBonusInstallmentRequest;
import com.forward.hrms.payroll.dto.request.CreateBonusEventRequest;
import com.forward.hrms.payroll.dto.request.CreateBonusInstallmentRequest;
import com.forward.hrms.payroll.dto.request.UpdateBonusEventRequest;
import com.forward.hrms.payroll.dto.request.UpdateBonusInstallmentRequest;
import com.forward.hrms.payroll.dto.response.BonusEventAmountDto;
import com.forward.hrms.payroll.dto.response.BonusEventDto;
import com.forward.hrms.payroll.dto.response.BonusInstallmentDto;
import com.forward.hrms.payroll.entity.BonusEvent;
import com.forward.hrms.payroll.entity.BonusEventAmount;
import com.forward.hrms.payroll.entity.BonusInstallment;
import com.forward.hrms.payroll.entity.EmployeePayrollSettings;
import com.forward.hrms.payroll.entity.EmployeeSalary;
import com.forward.hrms.payroll.enums.BonusEventStatus;
import com.forward.hrms.payroll.enums.BonusInstallmentStatus;
import com.forward.hrms.payroll.enums.OffCyclePaymentMode;
import com.forward.hrms.payroll.enums.PayrollErrorCode;
import com.forward.hrms.payroll.events.PayrollEvents;
import com.forward.hrms.payroll.repository.BonusEventAmountRepository;
import com.forward.hrms.payroll.repository.BonusEventRepository;
import com.forward.hrms.payroll.repository.BonusInstallmentRepository;
import com.forward.hrms.payroll.repository.EmployeePayrollSettingsRepository;
import com.forward.hrms.payroll.repository.FiscalYearRepository;
import com.forward.hrms.payroll.dto.response.BonusEventDtoMapper;
import com.forward.hrms.payroll.dto.response.BonusInstallmentDtoMapper;
import com.forward.hrms.payroll.dto.response.BonusEventAmountDtoMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BonusEventService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final BonusEventRepository events;
    private final BonusEventAmountRepository amounts;
    private final BonusInstallmentRepository installments;
    private final EmployeePayrollSettingsRepository settingsRepo;
    private final EmployeeSalaryService salaryService;
    private final FiscalYearRepository fyRepo;
    private final OffCyclePaymentService offCyclePayments;
    private final Outbox outbox;
    private final AuditEmitter audit;
    private final com.forward.hrms.commons.batch.BatchRunner batch;

    @Transactional(readOnly = true)
    public List<BonusEventDto> list(String fiscalYear, BonusEventStatus status) {
        return events.filter(fiscalYear, status).stream().map(BonusEventDtoMapper::of).toList();
    }

    @Transactional(readOnly = true)
    public BonusEventDto get(long id) {
        BonusEvent e = load(id);
        List<BonusInstallmentDto> ins = installments
                .findAllByBonusEventIdOrderByInstallmentNo(id).stream()
                .map(BonusInstallmentDtoMapper::of).toList();
        List<BonusEventAmountDto> amt = amounts.findAllByBonusEventIdOrderByEmployeeId(id).stream()
                .map(BonusEventAmountDtoMapper::of).toList();
        return BonusEventDtoMapper.of(e, ins, amt);
    }

    @Transactional
    public BonusEventDto create(CreateBonusEventRequest req) {
        if (!fyRepo.existsById(req.fiscalYearCode())) {
            throw new ConflictError(PayrollErrorCode.FISCAL_YEAR_NOT_FOUND,
                    "fiscal year " + req.fiscalYearCode() + " not found");
        }
        BonusEvent e = new BonusEvent();
        e.setFiscalYearCode(req.fiscalYearCode());
        e.setName(req.name());
        e.setEventType(req.eventType());
        e.setReferenceDate(req.referenceDate());
        if (req.fullServiceDaysThreshold() != null) {
            e.setFullServiceDaysThreshold(req.fullServiceDaysThreshold());
        }
        e.setStatus(BonusEventStatus.DRAFT);
        e.setNotes(req.notes());
        return BonusEventDtoMapper.of(events.save(e));
    }

    @Transactional
    public BonusEventDto update(long id, UpdateBonusEventRequest req) {
        BonusEvent e = load(id);
        if (e.getStatus() != BonusEventStatus.DRAFT) {
            throw new ConflictError(PayrollErrorCode.BONUS_EVENT_INVALID_STATE,
                    "edits allowed only on DRAFT events");
        }
        if (req.name() != null) e.setName(req.name());
        if (req.referenceDate() != null) e.setReferenceDate(req.referenceDate());
        if (req.fullServiceDaysThreshold() != null) e.setFullServiceDaysThreshold(req.fullServiceDaysThreshold());
        if (req.notes() != null) e.setNotes(req.notes());
        return BonusEventDtoMapper.of(e);
    }

    @Transactional
    public void delete(long id) {
        BonusEvent e = load(id);
        if (e.getStatus() != BonusEventStatus.DRAFT) {
            throw new ConflictError(PayrollErrorCode.BONUS_EVENT_INVALID_STATE,
                    "delete allowed only on DRAFT events");
        }
        events.delete(e);
    }

    /**
     * Freezes per-employee snapshots into {@code bonus_event_amounts}.
     * Eligibility = active payroll settings as of {@code referenceDate}.
     * Per-employee writes go through {@link com.forward.hrms.commons.batch.BatchRunner}
     * (REQUIRES_NEW) so a single bad row doesn't roll back the whole
     * activate; failed rows surface in the audit row's {@code failed}
     * list. The status flip + outbox + audit run in a closing tx after
     * the per-employee fan-out, mirroring {@link #applyInstallment}.
     */
    public BonusEventDto activate(long id) {
        BonusEvent ePre = batch.runInNewTx(() -> {
            BonusEvent e = load(id);
            if (e.getStatus() != BonusEventStatus.DRAFT) {
                throw new ConflictError(PayrollErrorCode.BONUS_EVENT_INVALID_STATE,
                        "activate requires DRAFT");
            }
            amounts.deleteAllByBonusEventId(id);
            return e;
        });
        LocalDate referenceDate = ePre.getReferenceDate();
        int threshold = ePre.getFullServiceDaysThreshold();

        List<EmployeePayrollSettings> all = settingsRepo.findAll();
        com.forward.hrms.commons.batch.BatchOutcomes<EmployeePayrollSettings, BigDecimal> outcomes =
                batch.runWithPartial(all, s -> {
                    EmployeeSalary salary;
                    try {
                        salary = salaryService.effectiveOn(s.getEmployeeId(), referenceDate);
                    } catch (NotFoundError nf) {
                        return BigDecimal.ZERO; // no salary on reference date → not eligible
                    }
                    int serviceDays = (int) ChronoUnit.DAYS.between(s.getHireDate(), referenceDate) + 1;
                    if (serviceDays <= 0) return BigDecimal.ZERO;
                    BigDecimal basicSnap = salary.getBasic();
                    BigDecimal fullBonus = serviceDays >= threshold
                            ? basicSnap
                            : Money.of(basicSnap)
                                    .proration(BigDecimal.valueOf(serviceDays), BigDecimal.valueOf(365))
                                    .amount();
                    BonusEventAmount a = new BonusEventAmount();
                    a.setBonusEventId(id);
                    a.setEmployeeId(s.getEmployeeId());
                    a.setHireDate(s.getHireDate());
                    a.setServiceDays(serviceDays);
                    a.setBasicSnapshot(basicSnap);
                    a.setFullBonus(fullBonus);
                    amounts.save(a);
                    return fullBonus;
                });

        BigDecimal totalFullBonus = BigDecimal.ZERO;
        int count = 0;
        for (var ok : outcomes.succeeded()) {
            BigDecimal v = ok.result();
            if (v != null && v.signum() > 0) {
                totalFullBonus = totalFullBonus.add(v);
                count++;
            }
        }

        final int finalCount = count;
        final BigDecimal finalTotal = totalFullBonus;
        return batch.runInNewTx(() -> {
            BonusEvent e = load(id);
            e.setStatus(BonusEventStatus.ACTIVE);
            e.setActivatedAt(LocalDateTime.now());
            e.setActivatedBy(CurrentUserHolder.get().map(c -> c.userId()).orElse(null));

            outbox.publish(PayrollEvents.TOPIC, "bonus_event:" + id,
                    PayrollEvents.BONUS_EVENT_ACTIVATED,
                    new PayrollEvents.BonusEventActivated(id, e.getName(), e.getEventType().name(),
                            e.getFiscalYearCode(), e.getReferenceDate(), finalCount, finalTotal,
                            CurrentUserHolder.usernameOrSystem()));
            Map<String, Object> auditAfter = new java.util.LinkedHashMap<>();
            auditAfter.put("employeeCount", finalCount);
            auditAfter.put("totalFullBonus", finalTotal);
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
            audit.emit("bonus_event", String.valueOf(id), "bonus_event_activated", null, auditAfter, true);
            return BonusEventDtoMapper.of(e);
        });
    }

    @Transactional
    public BonusEventDto close(long id) {
        BonusEvent e = load(id);
        if (e.getStatus() != BonusEventStatus.ACTIVE) {
            throw new ConflictError(PayrollErrorCode.BONUS_EVENT_INVALID_STATE,
                    "close requires ACTIVE");
        }
        e.setStatus(BonusEventStatus.CLOSED);
        return BonusEventDtoMapper.of(e);
    }

    // ----- installments -----

    @Transactional
    public BonusInstallmentDto addInstallment(long eventId, CreateBonusInstallmentRequest req) {
        BonusEvent e = load(eventId);
        if (e.getStatus() == BonusEventStatus.CLOSED) {
            throw new ConflictError(PayrollErrorCode.BONUS_EVENT_INVALID_STATE,
                    "event is CLOSED");
        }
        BigDecimal sum = installments.sumPercentageForEvent(eventId);
        if (sum.add(req.percentage()).compareTo(HUNDRED) > 0) {
            throw new ConflictError(PayrollErrorCode.BONUS_EVENT_PERCENTAGE_EXCEEDED,
                    "cumulative percentage would exceed 100%");
        }
        if (installments.findByBonusEventIdAndInstallmentNo(eventId, req.installmentNo()).isPresent()) {
            throw new ConflictError(PayrollErrorCode.BONUS_INSTALLMENT_INVALID_STATE,
                    "installment_no " + req.installmentNo() + " already exists");
        }
        BonusInstallment i = new BonusInstallment();
        i.setBonusEventId(eventId);
        i.setInstallmentNo(req.installmentNo());
        i.setPercentage(req.percentage());
        i.setPaidOn(req.paidOn());
        i.setStatus(BonusInstallmentStatus.DRAFT);
        return BonusInstallmentDtoMapper.of(installments.save(i));
    }

    @Transactional
    public BonusInstallmentDto updateInstallment(long eventId, long installmentId,
                                                 UpdateBonusInstallmentRequest req) {
        BonusInstallment i = loadInstallment(eventId, installmentId);
        if (i.getStatus() != BonusInstallmentStatus.DRAFT) {
            throw new ConflictError(PayrollErrorCode.BONUS_INSTALLMENT_INVALID_STATE,
                    "edits allowed only on DRAFT installments");
        }
        if (req.percentage() != null) {
            BigDecimal sum = installments.sumPercentageForEvent(eventId)
                    .subtract(i.getPercentage()).add(req.percentage());
            if (sum.compareTo(HUNDRED) > 0) {
                throw new ConflictError(PayrollErrorCode.BONUS_EVENT_PERCENTAGE_EXCEEDED,
                        "cumulative percentage would exceed 100%");
            }
            i.setPercentage(req.percentage());
        }
        if (req.paidOn() != null) i.setPaidOn(req.paidOn());
        return BonusInstallmentDtoMapper.of(i);
    }

    @Transactional
    public void deleteInstallment(long eventId, long installmentId) {
        BonusInstallment i = loadInstallment(eventId, installmentId);
        if (i.getStatus() != BonusInstallmentStatus.DRAFT) {
            throw new ConflictError(PayrollErrorCode.BONUS_INSTALLMENT_INVALID_STATE,
                    "delete allowed only on DRAFT installments");
        }
        installments.delete(i);
    }

    /**
     * §7.payroll #51 — orchestrator pattern. Per-employee
     * {@code REQUIRES_NEW} so a single off-cycle write that fails
     * (e.g. employee left mid-cycle) doesn't roll back the whole
     * installment. The installment status flip + outbox/audit
     * happen in a closing tx after the per-row fan-out.
     */
    public BonusInstallmentDto applyInstallment(long eventId, long installmentId,
                                                ApplyBonusInstallmentRequest req) {
        BonusInstallment iPre = loadInstallment(eventId, installmentId);
        if (iPre.getStatus() == BonusInstallmentStatus.APPLIED) {
            return BonusInstallmentDtoMapper.of(iPre);   // idempotent re-apply
        }
        BonusEvent e = load(eventId);
        if (e.getStatus() != BonusEventStatus.ACTIVE) {
            throw new ConflictError(PayrollErrorCode.BONUS_EVENT_INVALID_STATE,
                    "event must be ACTIVE to apply installments");
        }
        OffCyclePaymentMode mode = req.paymentMode() == null ? OffCyclePaymentMode.BANK : req.paymentMode();
        BigDecimal pctFraction = iPre.getPercentage().divide(HUNDRED, 8, RoundingMode.CEILING);
        LocalDate paidOn = iPre.getPaidOn();
        int installmentNo = iPre.getInstallmentNo();
        BigDecimal percentage = iPre.getPercentage();

        List<BonusEventAmount> rows = amounts.findAllByBonusEventIdOrderByEmployeeId(eventId);

        com.forward.hrms.commons.batch.BatchOutcomes<BonusEventAmount, BigDecimal> outcomes =
                batch.runWithPartial(rows, a -> {
                    BigDecimal amount = Money.of(a.getFullBonus()).multiply(pctFraction).amount();
                    if (amount.signum() <= 0) return BigDecimal.ZERO;
                    offCyclePayments.recordBonusInstallment(a.getEmployeeId(), amount, paidOn,
                            e.getName() + " — installment " + installmentNo +
                                    " (" + percentage.stripTrailingZeros().toPlainString() + "%)",
                            mode, eventId, installmentId);
                    return amount;
                });

        BigDecimal totalAmount = BigDecimal.ZERO;
        int count = 0;
        for (var ok : outcomes.succeeded()) {
            if (ok.result() != null && ok.result().signum() > 0) {
                totalAmount = totalAmount.add(ok.result());
                count++;
            }
        }

        // Closing tx — flip the installment + write outbox/audit. Use
        // a fresh tx so the per-row fan-out's commits land before this
        // writes the "done" marker (matters on retry: reapply will
        // skip already-paid rows via off-cycle's idempotency key).
        final int finalCount = count;
        final BigDecimal finalTotal = totalAmount;
        return batch.runInNewTx(() -> {
            BonusInstallment i = loadInstallment(eventId, installmentId);
            i.setStatus(BonusInstallmentStatus.APPLIED);
            i.setAppliedAt(LocalDateTime.now());
            outbox.publish(PayrollEvents.TOPIC, "bonus_installment:" + installmentId,
                    PayrollEvents.BONUS_INSTALLMENT_APPLIED,
                    new PayrollEvents.BonusInstallmentApplied(installmentId, eventId, e.getName(),
                            i.getInstallmentNo(), i.getPercentage(), i.getPaidOn(),
                            finalCount, finalTotal, CurrentUserHolder.usernameOrSystem()));
            Map<String, Object> auditAfter = new java.util.LinkedHashMap<>();
            auditAfter.put("eventId", eventId);
            auditAfter.put("employeeCount", finalCount);
            auditAfter.put("totalAmount", finalTotal);
            if (!outcomes.failed().isEmpty()) {
                auditAfter.put("failedCount", outcomes.failedCount());
                List<Map<String, Object>> sample = new java.util.ArrayList<>();
                int max = Math.min(20, outcomes.failed().size());
                for (int j = 0; j < max; j++) {
                    var f = outcomes.failed().get(j);
                    sample.add(Map.of("employeeId", f.item().getEmployeeId(),
                            "errorCode", f.errorCode(),
                            "message", f.message()));
                }
                auditAfter.put("failed", sample);
            }
            audit.emit("bonus_installment", String.valueOf(installmentId),
                    "bonus_installment_applied", null, auditAfter, true);
            return BonusInstallmentDtoMapper.of(i);
        });
    }

    BonusEvent load(long id) {
        return events.findById(id).orElseThrow(
                () -> new NotFoundError(PayrollErrorCode.BONUS_EVENT_NOT_FOUND, "bonus event not found"));
    }

    BonusInstallment loadInstallment(long eventId, long installmentId) {
        BonusInstallment i = installments.findById(installmentId).orElseThrow(
                () -> new NotFoundError(PayrollErrorCode.BONUS_INSTALLMENT_NOT_FOUND,
                        "installment not found"));
        if (!i.getBonusEventId().equals(eventId)) {
            throw new NotFoundError(PayrollErrorCode.BONUS_INSTALLMENT_NOT_FOUND,
                    "installment not found");
        }
        return i;
    }
}
