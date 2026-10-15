package com.forward.hrms.payroll.entity;

import com.forward.hrms.commons.core.jpa.AuditedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Per-employee selected component (allowance or deduction). Effective
 * dated — updating the amount closes the current row and inserts a new
 * one. Income tax lives here too as a DEDUCTION line with
 * {@code component.code='INCOME_TAX'}.
 */
@Entity
@Table(name = "employee_salary_components")
@Getter
@Setter
public class EmployeeSalaryComponent extends AuditedEntity {

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "component_id", nullable = false)
    private Long componentId;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "notes")
    private String notes;

}
