package com.forward.hrms.payroll.entity;

import com.forward.hrms.commons.core.jpa.AuditedEntity;
import com.forward.hrms.payroll.enums.LeaveEncashmentStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Payment for unused annual leaves accrued by an employee. Calculated
 * as {@code days_encashed × (basic_salary / 26)} (one-month divisor).
 * Paid via a regular payroll run ({@code payroll_run_id} set) or
 * off-cycle (run id null). One row per (employee, calculation_month).
 */
@Entity
@Table(name = "leave_encashments", uniqueConstraints = @UniqueConstraint(
                name = "uq_le_employee_period",
                columnNames = {"employee_id", "calculation_month"}))
@Getter
@Setter
public class LeaveEncashment extends AuditedEntity {

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "calculation_month", nullable = false)
    private LocalDate calculationMonth;

    @Column(name = "period_in_months", nullable = false)
    private int periodInMonths;

    @Column(name = "days_encashed", nullable = false, precision = 5, scale = 2)
    private BigDecimal daysEncashed;

    @Column(name = "basic_salary", nullable = false, precision = 15, scale = 2)
    private BigDecimal basicSalary;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 16)
    private LeaveEncashmentStatus paymentStatus;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "payroll_run_id")
    private Long payrollRunId;

    @Column(name = "description")
    private String description;

    /**
     * Set only when this row was created by a Final Settlement's closing
     * step. Lets {@code LeaveSettlementService.reverse} find and delete
     * exactly the rows a rejected settlement created, without touching
     * rows from a regular payroll run or a different settlement.
     */
    @Column(name = "final_settlement_id")
    private Long finalSettlementId;
}
