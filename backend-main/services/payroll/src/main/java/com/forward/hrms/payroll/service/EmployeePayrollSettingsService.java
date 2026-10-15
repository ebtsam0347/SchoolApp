package com.forward.hrms.payroll.service;

import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.kafka.outbox.Outbox;
import com.forward.hrms.payroll.client.EmployeeClient;
import com.forward.hrms.payroll.dto.request.UpdateEmployeePayrollSettingsRequest;
import com.forward.hrms.payroll.dto.response.EmployeePayrollSettingsDto;
import com.forward.hrms.payroll.entity.EmployeePayrollSettings;
import com.forward.hrms.payroll.enums.PayrollErrorCode;
import com.forward.hrms.payroll.enums.PaymentMode;
import com.forward.hrms.payroll.events.PayrollEvents;
import com.forward.hrms.payroll.repository.EmployeePayrollSettingsRepository;
import com.forward.hrms.payroll.dto.response.EmployeePayrollSettingsDtoMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeePayrollSettingsService {

    private final EmployeePayrollSettingsRepository repo;
    private final Outbox outbox;
    private final AuditEmitter audit;
    private final EmployeeClient employees;

    @Transactional(readOnly = true)
    public EmployeePayrollSettingsDto get(long employeeId) {
        return EmployeePayrollSettingsDtoMapper.of(load(employeeId));
    }

    @Transactional(readOnly = true)
    public EmployeePayrollSettings load(long employeeId) {
        return repo.findByEmployeeId(employeeId).orElseThrow(
                () -> new NotFoundError(PayrollErrorCode.SETTINGS_NOT_FOUND,
                        "no payroll settings for employee " + employeeId));
    }

    /**
     * Idempotent — re-running with the same hire_date for an existing
     * employee is a no-op. Called from the {@code employee.created}
     * consumer.
     */
    @Transactional
    public EmployeePayrollSettings ensureExists(long employeeId, LocalDate hireDate) {
        return repo.findByEmployeeId(employeeId).orElseGet(() -> {
            EmployeePayrollSettings s = new EmployeePayrollSettings();
            s.setEmployeeId(employeeId);
            s.setHireDate(hireDate);
            s.setEobiEnrolled(true);
            s.setPaymentMode(PaymentMode.BANK_TRANSFER);
            s.setGratuityStartDate(hireDate);
            return repo.save(s);
        });
    }

    /**
     * Flips {@code active} false on termination. {@code computeRun} now
     * selects via {@code settingsRepo.findAllByActiveTrue()}, which is
     * what actually stops the monthly payslip generation (Audit Finding
     * 4 — this used to only flip {@code eobiEnrolled}, an unrelated
     * field, leaving the employee in the regular-run selection
     * indefinitely). Idempotent — if the row is already gone or already
     * inactive (re-delivery of {@code employee.terminated}), no-op.
     */
    @Transactional
    public void markInactive(long employeeId) {
        repo.findByEmployeeId(employeeId).ifPresent(setting -> {
            setting.setEobiEnrolled(false);
            setting.setActive(false);
        });
    }

    /**
     * Reverses {@link #markInactive}. Fired when HR rejects/cancels a
     * final settlement and the employee is reinstated. Idempotent —
     * re-enabling an already-active row is a no-op.
     */
    @Transactional
    public void markActive(long employeeId) {
        repo.findByEmployeeId(employeeId).ifPresent(setting -> {
            setting.setEobiEnrolled(true);
            setting.setActive(true);
        });
    }

    @Transactional
    public EmployeePayrollSettingsDto update(long employeeId, UpdateEmployeePayrollSettingsRequest req) {
        EmployeePayrollSettings s = repo.findByEmployeeId(employeeId).orElseGet(() -> {
            // Race window: onboarding PATCH may land before the
            // employee.created consumer has seeded the row. Look up
            // joiningDate from employee-service and create on the fly.
            // Null = employee doesn't exist or employee-service is down,
            // surface the original 404 so the FE sees a meaningful error.
            LocalDate hireDate = employees.fetchJoiningDate(employeeId);
            if (hireDate == null) {
                throw new NotFoundError(PayrollErrorCode.SETTINGS_NOT_FOUND,
                        "no payroll settings for employee " + employeeId);
            }
            log.info("payroll-settings PATCH preceded employee.created consumer for employee {}, upserting", employeeId);
            return ensureExists(employeeId, hireDate);
        });
        String actor = CurrentUserHolder.usernameOrSystem();

        if (req.eobiEnrolled() != null && req.eobiEnrolled() != s.isEobiEnrolled()) {
            s.setEobiEnrolled(req.eobiEnrolled());
            outbox.publish(PayrollEvents.TOPIC, "employee:" + employeeId,
                    PayrollEvents.EOBI_ENROLLMENT_CHANGED,
                    new PayrollEvents.EobiEnrollmentChanged(employeeId, s.isEobiEnrolled(), actor));
            audit.emit("employee_payroll_settings", String.valueOf(employeeId),
                    "eobi_enrollment_changed", null,
                    Map.of("eobiEnrolled", s.isEobiEnrolled()), true);
        }
        if (req.paymentMode() != null && req.paymentMode() != s.getPaymentMode()) {
            PaymentMode old = s.getPaymentMode();
            s.setPaymentMode(req.paymentMode());
            outbox.publish(PayrollEvents.TOPIC, "employee:" + employeeId,
                    PayrollEvents.PAYMENT_MODE_CHANGED,
                    new PayrollEvents.PaymentModeChanged(employeeId, old.name(), s.getPaymentMode().name(), actor));
            audit.emit("employee_payroll_settings", String.valueOf(employeeId),
                    "payment_mode_changed",
                    Map.of("paymentMode", old.name()),
                    Map.of("paymentMode", s.getPaymentMode().name()), true);
        }
        if (req.gratuityStartDate() != null
                && !req.gratuityStartDate().equals(s.getGratuityStartDate())) {
            LocalDate old = s.getGratuityStartDate();
            s.setGratuityStartDate(req.gratuityStartDate());
            audit.emit("employee_payroll_settings", String.valueOf(employeeId),
                    "gratuity_start_date_changed",
                    old == null ? null : Map.of("gratuityStartDate", old.toString()),
                    Map.of("gratuityStartDate", s.getGratuityStartDate().toString()), true);
        }
        if (req.seniorAllowanceStartDate() != null
                && !req.seniorAllowanceStartDate().equals(s.getSeniorAllowanceStartDate())) {
            LocalDate old = s.getSeniorAllowanceStartDate();
            s.setSeniorAllowanceStartDate(req.seniorAllowanceStartDate());
            audit.emit("employee_payroll_settings", String.valueOf(employeeId),
                    "senior_allowance_start_date_changed",
                    old == null ? null : Map.of("seniorAllowanceStartDate", old.toString()),
                    Map.of("seniorAllowanceStartDate", s.getSeniorAllowanceStartDate().toString()), true);
        }
        return EmployeePayrollSettingsDtoMapper.of(s);
    }
}
