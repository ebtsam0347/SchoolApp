package com.forward.hrms.payroll.service;

import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.web.page.PageResponse;
import com.forward.hrms.payroll.dto.request.CreateLeaveEncashmentRequest;
import com.forward.hrms.payroll.dto.request.MarkLeaveEncashmentPaidRequest;
import com.forward.hrms.payroll.dto.response.LeaveEncashmentDto;
import com.forward.hrms.payroll.entity.LeaveEncashment;
import com.forward.hrms.payroll.enums.LeaveEncashmentStatus;
import com.forward.hrms.payroll.enums.PayrollErrorCode;
import com.forward.hrms.payroll.repository.LeaveEncashmentRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Annual leave encashment. Amount = {@code daysEncashed × (basicSalary / 26)},
 * rounded up per CLAUDE.md money rules. The 26-day divisor matches the
 * legacy WebHRMS convention (days-per-month for salaried staff).
 */
@Service
@RequiredArgsConstructor
public class LeaveEncashmentService {

    private static final BigDecimal DAY_DIVISOR = new BigDecimal("26");

    private final LeaveEncashmentRepository repo;
    private final EmployeeSalaryService salaries;
    private final AuditEmitter audit;
    private final com.forward.hrms.payroll.repository.PayrollRunRepository payrollRuns;

    @Transactional(readOnly = true)
    public PageResponse<LeaveEncashmentDto> search(Long employeeId, LeaveEncashmentStatus status,
                                                   Pageable pageable) {
        Page<LeaveEncashment> page = repo.search(employeeId, status, pageable);
        return PageResponse.from(page, page.getContent().stream().map(LeaveEncashmentDto::of).toList());
    }

    @Transactional(readOnly = true)
    public LeaveEncashmentDto get(long id) {
        return LeaveEncashmentDto.of(require(id));
    }

    @Transactional
    public LeaveEncashmentDto create(CreateLeaveEncashmentRequest req) {
        // §7.payroll #45 — daysEncashed must be a positive number. Zero or
        // negative is either a typo or a stale FE form; either way it
        // produces a 0-amount row that nobody wants.
        if (req.daysEncashed() == null || req.daysEncashed().signum() <= 0) {
            throw new com.forward.hrms.commons.core.error.UnprocessableError(
                    PayrollErrorCode.LEAVE_ENCASHMENT_DAYS_INVALID,
                    "daysEncashed must be > 0");
        }
        LocalDate periodStart = req.calculationMonth().withDayOfMonth(1);
        if (repo.existsByEmployeeIdAndCalculationMonth(req.employeeId(), periodStart)) {
            throw new ConflictError(PayrollErrorCode.LEAVE_ENCASHMENT_DUPLICATE_PERIOD,
                    "encashment already exists for this employee + period");
        }
        // §7.payroll — basic salary is sourced from the canonical
        // EmployeeSalary effective on the encashment's calculation month
        // (NOT from the caller). Caller-supplied basic was a footgun:
        // an HR typo or a stale FE form would compute against the wrong
        // basic and silently shrink/inflate the payout.
        BigDecimal basic = salaries.effectiveOn(req.employeeId(), periodStart).getBasic();
        BigDecimal amount = computeAmount(req.daysEncashed(), basic);

        LeaveEncashment e = new LeaveEncashment();
        e.setEmployeeId(req.employeeId());
        e.setCalculationMonth(periodStart);
        e.setPeriodInMonths(req.periodInMonths());
        e.setDaysEncashed(req.daysEncashed());
        e.setBasicSalary(basic);
        e.setAmount(amount);
        e.setPaymentStatus(LeaveEncashmentStatus.PENDING);
        e.setDescription(req.description());
        repo.save(e);
        audit.emit("leave_encashment", String.valueOf(e.getId()), "created",
                null, Map.of("employeeId", req.employeeId(), "amount", amount), true);
        return LeaveEncashmentDto.of(e);
    }

    @Transactional
    public LeaveEncashmentDto markPaid(long id, MarkLeaveEncashmentPaidRequest body) {
        LeaveEncashment e = require(id);
        if (e.getPaymentStatus() != LeaveEncashmentStatus.PENDING) {
            throw new ConflictError(PayrollErrorCode.LEAVE_ENCASHMENT_INVALID_STATE,
                    "only PENDING encashments can be marked paid");
        }
        Long payrollRunId = body == null ? null : body.payrollRunId();
        if (payrollRunId != null) PayrollRunGuard.requireWritable(payrollRuns, payrollRunId);
        e.setPaymentStatus(LeaveEncashmentStatus.PAID);
        e.setPaidAt(LocalDateTime.now());
        e.setPayrollRunId(payrollRunId);
        audit.emit("leave_encashment", String.valueOf(id), "paid",
                null, Map.of("payrollRunId", payrollRunId == null
                        ? "off_cycle" : payrollRunId.toString()), true);
        return LeaveEncashmentDto.of(e);
    }

    @Transactional
    public LeaveEncashmentDto cancel(long id) {
        LeaveEncashment e = require(id);
        if (e.getPaymentStatus() == LeaveEncashmentStatus.PAID) {
            throw new ConflictError(PayrollErrorCode.LEAVE_ENCASHMENT_INVALID_STATE,
                    "paid encashments cannot be cancelled");
        }
        e.setPaymentStatus(LeaveEncashmentStatus.CANCELLED);
        audit.emit("leave_encashment", String.valueOf(id), "cancelled", null, Map.of(), true);
        return LeaveEncashmentDto.of(e);
    }

    private BigDecimal computeAmount(BigDecimal daysEncashed, BigDecimal basicSalary) {
        BigDecimal perDay = basicSalary.divide(DAY_DIVISOR, 4, RoundingMode.CEILING);
        return perDay.multiply(daysEncashed).setScale(2, RoundingMode.CEILING);
    }

    private LeaveEncashment require(long id) {
        return repo.findById(id)
                .orElseThrow(() -> new NotFoundError(PayrollErrorCode.LEAVE_ENCASHMENT_NOT_FOUND,
                        "leave encashment not found"));
    }
}
