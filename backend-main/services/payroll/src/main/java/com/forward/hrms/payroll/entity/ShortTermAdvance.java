package com.forward.hrms.payroll.entity;

import com.forward.hrms.commons.core.jpa.AuditedEntity;
import com.forward.hrms.payroll.enums.ShortTermAdvanceStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Per-employee detail row in a {@link ShortTermAdvanceBatch}. Approved
 * rows are deducted in full from the target month's payroll run.
 */
@Entity
@Table(name = "short_term_advances")
@Getter
@Setter
public class ShortTermAdvance extends AuditedEntity {

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "target_year", nullable = false)
    private int targetYear;

    @Column(name = "target_month", nullable = false)
    private int targetMonth;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ShortTermAdvanceStatus status;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "deducted_in_run_id")
    private Long deductedInRunId;

    /**
     * Set when a Final Settlement's closing step recovered this advance.
     * Payroll and later settlements skip such rows; lets
     * {@code ShortTermAdvanceSettlementService.reverse} find and unlink
     * exactly the rows a rejected settlement recovered.
     */
    @Column(name = "final_settlement_id")
    private Long finalSettlementId;
}
