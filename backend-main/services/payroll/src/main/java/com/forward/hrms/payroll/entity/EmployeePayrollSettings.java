package com.forward.hrms.payroll.entity;

import com.forward.hrms.commons.core.jpa.AuditedEntity;
import com.forward.hrms.payroll.enums.PaymentMode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "employee_payroll_settings")
@Getter
@Setter
public class EmployeePayrollSettings extends AuditedEntity {

    @Column(name = "employee_id", nullable = false, unique = true)
    private Long employeeId;

    @Column(name = "hire_date", nullable = false)
    private LocalDate hireDate;

    @Column(name = "eobi_enrolled", nullable = false)
    private boolean eobiEnrolled;

    /** False once the employee is terminated — excludes them from
     *  {@code PayrollComputeService}'s regular-run employee selection.
     *  See {@code EmployeePayrollSettingsService.markInactive}. */
    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_mode", nullable = false, length = 16)
    private PaymentMode paymentMode = PaymentMode.BANK_TRANSFER;

    /** When gratuity accrual began. Usually equals hireDate but can differ. */
    @Column(name = "gratuity_start_date")
    private LocalDate gratuityStartDate;

    /** When senior-allowance eligibility began. Null = ineligible. */
    @Column(name = "senior_allowance_start_date")
    private LocalDate seniorAllowanceStartDate;
}
