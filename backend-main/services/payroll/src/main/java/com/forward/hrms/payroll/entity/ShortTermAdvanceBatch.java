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

/**
 * Bulk short-term advance request raised by a department head on behalf
 * of N employees, deducted in full from {@code (target_year, target_month)}
 * payroll. Approval flow mirrors {@link PermanentAdvance}: the raising
 * department head's manager, then HR, then Accounts. Per-employee detail
 * lives on {@link ShortTermAdvance}.
 */
@Entity
@Table(name = "short_term_advance_batches")
@Getter
@Setter
public class ShortTermAdvanceBatch extends AuditedEntity {

    @Column(name = "request_code", nullable = false, length = 20, updatable = false, unique = true)
    private String requestCode;

    @Column(name = "department_id")
    private Long departmentId;

    @Column(name = "section_id")
    private Long sectionId;

    @Column(name = "target_year", nullable = false)
    private int targetYear;

    @Column(name = "target_month", nullable = false)
    private int targetMonth;

    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "total_employees", nullable = false)
    private int totalEmployees = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private AdvanceStatus status;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "notes")
    private String notes;
}
