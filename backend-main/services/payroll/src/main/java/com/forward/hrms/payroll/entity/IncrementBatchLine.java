package com.forward.hrms.payroll.entity;

import com.forward.hrms.commons.core.jpa.AuditedEntity;
import com.forward.hrms.payroll.enums.IncrementBatchLineStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "increment_batch_lines")
@Getter
@Setter
public class IncrementBatchLine extends AuditedEntity {

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "old_basic", nullable = false, precision = 15, scale = 2)
    private BigDecimal oldBasic;

    @Column(name = "new_basic", nullable = false, precision = 15, scale = 2)
    private BigDecimal newBasic;

    @Column(name = "old_income_tax", precision = 15, scale = 2)
    private BigDecimal oldIncomeTax;

    @Column(name = "new_income_tax", precision = 15, scale = 2)
    private BigDecimal newIncomeTax;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private IncrementBatchLineStatus status = IncrementBatchLineStatus.VALID;

    @Column(name = "error_message")
    private String errorMessage;
}
