package com.forward.hrms.payroll.entity;

import com.forward.hrms.commons.core.jpa.AuditedEntity;
import com.forward.hrms.payroll.enums.AdvanceStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;

/**
 * Long-term loan against future salary. Once APPROVED the payroll run
 * deducts monthly installments (see {@link PermanentAdvanceDeduction})
 * until the employee's balance hits zero; status stays APPROVED. Final
 * settlement stamps {@code closed_at}. Three-stage approval: Manager, then HR, then Accounts.
 *
 * <p>{@code gratuityAtRequest} is a snapshot of the employee's gratuity
 * accrual at request time — used as a collateral check ("don't lend more
 * than the employee has earned"); not mutated after.
 */
@Entity
@Table(name = "permanent_advances")
@Getter
@Setter
public class PermanentAdvance extends AuditedEntity {

    @Column(name = "request_code", nullable = false, length = 20, updatable = false, unique = true)
    private String requestCode;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "monthly_deduction", nullable = false, precision = 15, scale = 2)
    private BigDecimal monthlyDeduction;

    @Column(name = "bonus_offset", nullable = false, precision = 15, scale = 2)
    private BigDecimal bonusOffset = BigDecimal.ZERO;

    @Column(name = "annual_leave_offset", nullable = false, precision = 15, scale = 2)
    private BigDecimal annualLeaveOffset = BigDecimal.ZERO;

    @Column(name = "gratuity_at_request", precision = 15, scale = 2)
    private BigDecimal gratuityAtRequest;

    @Column(name = "receiving_date")
    private LocalDate receivingDate;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private AdvanceStatus status;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    /** §7.payroll #38 — disbursement timestamp. Legacy rows only; no longer written. */
    @Column(name = "disbursed_at")
    private LocalDateTime disbursedAt;

    /** §7.payroll #38 — disbursing user id. Populated alongside {@link #disbursedAt}. */
    @Column(name = "disbursed_by")
    private Long disbursedBy;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "fiscal_year_code")
    private String fiscalYearCode;

    @Column(name = "bonus_offset2", nullable = false, precision = 15, scale = 2)
    private BigDecimal bonusOffset2 = BigDecimal.ZERO;

    @Column(name = "annual_leave_month")
    private LocalDate annualLeaveMonth;

    /** Employee's date of joining, snapshotted from employee-service at create time. Null on older rows. */
    @Column(name = "joining_date")
    private LocalDate joiningDate;

}
