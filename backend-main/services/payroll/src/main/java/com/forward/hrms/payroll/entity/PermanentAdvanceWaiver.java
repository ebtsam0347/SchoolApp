package com.forward.hrms.payroll.entity;

import com.forward.hrms.commons.core.jpa.AuditedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * HR waived this employee's permanent-advance installment for one payroll
 * month. Compute deducts 0 that month and records a 0 SALARY row in the
 * ledger; the advance's {@code monthly_deduction} is left alone, so the
 * next month deducts as usual. Keyed by period, so it survives a run reset.
 */
@Entity
@Table(name = "permanent_advance_waivers")
@Getter
@Setter
public class PermanentAdvanceWaiver extends AuditedEntity {

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "period_year", nullable = false)
    private Integer periodYear;

    @Column(name = "period_month", nullable = false)
    private Integer periodMonth;
}
