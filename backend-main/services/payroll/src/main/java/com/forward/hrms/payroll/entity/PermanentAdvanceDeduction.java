package com.forward.hrms.payroll.entity;

import com.forward.hrms.commons.core.jpa.AuditedEntity;
import com.forward.hrms.payroll.enums.PermanentAdvanceDeductionSource;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One recovery against an employee's permanent advances (they are one pool —
 * balance = APPROVED advances − all deductions). The amount is always in
 * {@code deductedAmount}, and {@code source} / {@code sourceRefId} say where
 * it came from (see {@link PermanentAdvanceDeductionSource}). Undoing the
 * source deletes its rows by that pair. {@code remainingBalance} is a
 * snapshot — the source of truth is the running sum across rows.
 */
@Entity
@Table(name = "permanent_advance_deductions")
@Getter
@Setter
public class PermanentAdvanceDeduction extends AuditedEntity {

    @Column(name = "deducted_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal deductedAmount;

    @Column(name = "remaining_balance", nullable = false, precision = 15, scale = 2)
    private BigDecimal remainingBalance;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 16)
    private PermanentAdvanceDeductionSource source;

    @Column(name = "source_ref_id")
    private Long sourceRefId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;
}
