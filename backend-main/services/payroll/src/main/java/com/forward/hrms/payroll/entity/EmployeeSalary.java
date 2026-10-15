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
 * Per-employee monthly basic. Effective-dated — a revision closes the
 * previous current row and inserts a new one.
 */
@Entity
@Table(name = "employee_salaries")
@Getter
@Setter
public class EmployeeSalary extends AuditedEntity {

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "basic", nullable = false, precision = 15, scale = 2)
    private BigDecimal basic;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "revision_reason")
    private String revisionReason;
}
