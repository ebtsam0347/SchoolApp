package com.forward.hrms.payroll.service;

import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.kafka.outbox.Outbox;
import com.forward.hrms.payroll.dto.request.ReviseBasicSalaryRequest;
import com.forward.hrms.payroll.dto.response.EmployeeSalaryDto;
import com.forward.hrms.payroll.entity.EmployeeSalary;
import com.forward.hrms.payroll.enums.PayrollErrorCode;
import com.forward.hrms.payroll.events.PayrollEvents;
import com.forward.hrms.payroll.repository.EmployeeSalaryRepository;
import com.forward.hrms.payroll.dto.response.EmployeeSalaryDtoMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EmployeeSalaryService {

    private final EmployeeSalaryRepository repo;
    private final Outbox outbox;
    private final AuditEmitter audit;

    @Transactional(readOnly = true)
    public EmployeeSalaryDto current(long employeeId) {
        return EmployeeSalaryDtoMapper.of(currentEntity(employeeId));
    }

    @Transactional(readOnly = true)
    public EmployeeSalary currentEntity(long employeeId) {
        return repo.findCurrent(employeeId).orElseThrow(
                () -> new NotFoundError(PayrollErrorCode.SALARY_NOT_FOUND,
                        "no current basic salary for employee " + employeeId));
    }

    @Transactional(readOnly = true)
    public EmployeeSalary effectiveOn(long employeeId, LocalDate asOf) {
        List<EmployeeSalary> rows = repo.findEffectiveOn(employeeId, asOf);
        if (rows.isEmpty()) {
            throw new NotFoundError(PayrollErrorCode.SALARY_NOT_FOUND,
                    "no salary effective on " + asOf + " for employee " + employeeId);
        }
        return rows.getFirst();
    }


    /**
     * Non-throwing variant of {@link #effectiveOn}. Use this from callers
     * that already sit inside a transaction and want to swallow "no row
     * yet" — throwing across a nested {@code @Transactional} boundary
     * marks the shared tx rollback-only even if the caller catches, which
     * trips {@code UnexpectedRollbackException} at outer commit time.
     * Gratuity's "not yet eligible" branch is the canonical caller.
     */
    @Transactional(readOnly = true)
    public java.util.Optional<EmployeeSalary> effectiveOnOrEmpty(long employeeId, LocalDate asOf) {
        return repo.findEffectiveOn(employeeId, asOf).stream().findFirst();
    }

    @Transactional(readOnly = true)
    public List<EmployeeSalaryDto> history(long employeeId) {
        return repo.findAllByEmployeeIdOrderByEffectiveFromDesc(employeeId).stream()
                .map(EmployeeSalaryDtoMapper::of).toList();
    }

    /**
     * Revise basic. Closes the existing current row (effective_to = effectiveFrom - 1)
     * and inserts a new open-ended row.
     */
    @Transactional
    public EmployeeSalaryDto revise(long employeeId, ReviseBasicSalaryRequest req) {
        return EmployeeSalaryDtoMapper.of(reviseInternal(employeeId, req.basic(),
                req.effectiveFrom(), req.revisionReason(), true));
    }

    /**
     * Like {@link #revise} but called from increment-batch apply. The
     * caller emits the batch-level audit event itself; we still emit the
     * per-employee {@code payroll.basic_revised} so downstream consumers
     * see the same trail as a manual revision.
     */
    @Transactional
    public EmployeeSalary reviseInternal(long employeeId, BigDecimal newBasic,
                                         LocalDate effectiveFrom, String reason,
                                         boolean emitAudit) {
        boolean closedExisting = repo.findCurrent(employeeId).map(existing -> {
            if (!effectiveFrom.isAfter(existing.getEffectiveFrom())) {
                // §7.payroll #63 — this is a validation conflict, not a
                // missing row. SALARY_NOT_FOUND on the FE rendered as
                // "404 — salary missing" which misled HR. Use a code that
                // names what's actually wrong.
                throw new com.forward.hrms.commons.core.error.ConflictError(
                        PayrollErrorCode.SALARY_REVISION_INVALID,
                        "effectiveFrom must be after the current row's effectiveFrom (" +
                                existing.getEffectiveFrom() + ")");
            }
            existing.setEffectiveTo(effectiveFrom.minusDays(1));
            return true;
        }).orElse(false);
        // Flush the close-out UPDATE before issuing the INSERT below.
        // Hibernate's action queue runs INSERTs before UPDATEs by default,
        // which would briefly leave two rows with effective_to=null and
        // trip the uq_emp_salaries_current partial unique index.
        if (closedExisting) repo.flush();

        EmployeeSalary s = new EmployeeSalary();
        s.setEmployeeId(employeeId);
        s.setBasic(newBasic);
        s.setEffectiveFrom(effectiveFrom);
        s.setRevisionReason(reason);
        s = repo.save(s);

        outbox.publish(PayrollEvents.TOPIC, "employee:" + employeeId,
                PayrollEvents.BASIC_REVISED,
                new PayrollEvents.BasicRevised(employeeId, newBasic, effectiveFrom,
                        CurrentUserHolder.usernameOrSystem()));
        if (emitAudit) {
            audit.emit("employee_salary", String.valueOf(employeeId), "basic_revised",
                    null,
                    Map.of("basic", String.valueOf(newBasic),
                            "effectiveFrom", effectiveFrom.toString()),
                    true);
        }
        return s;
    }

    /**
     * Close the current open-ended salary row on termination. Idempotent —
     * if there is no open row (already closed), no-op. After this the
     * employee's salary {@link #effectiveOn} for any date past
     * {@code lastWorkingDay} returns nothing, which is what stops the
     * monthly compute loop from generating further payslips.
     */
    @Transactional
    public void closeOnExit(long employeeId, LocalDate lastWorkingDay) {
        repo.findCurrent(employeeId).ifPresent(existing -> {
            if (existing.getEffectiveTo() == null
                    || existing.getEffectiveTo().isAfter(lastWorkingDay)) {
                existing.setEffectiveTo(lastWorkingDay);
            }
        });
    }

    /**
     * Reverses {@link #closeOnExit}. Fired when HR rejects a final
     * settlement and {@code employee.reinstated} arrives — reopens the
     * row {@code closeOnExit} closed at {@code lastWorkingDay} so the
     * employee has a current basic salary again. Idempotent: no-op if
     * a current row already exists (already reopened, or redelivered
     * event) or if no row was closed on that exact date.
     */
    @Transactional
    public void reopenOnExit(long employeeId, LocalDate lastWorkingDay) {
        if (repo.findCurrent(employeeId).isPresent()) return;
        repo.findAllByEmployeeIdAndEffectiveToOrderByEffectiveFromDesc(employeeId, lastWorkingDay)
                .stream().findFirst()
                .ifPresent(row -> row.setEffectiveTo(null));
    }
}
