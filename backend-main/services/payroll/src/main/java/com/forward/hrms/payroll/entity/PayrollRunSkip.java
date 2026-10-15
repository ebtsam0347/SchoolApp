package com.forward.hrms.payroll.entity;

import com.forward.hrms.commons.core.jpa.BaseLongIdEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Per-employee skip reason captured during compute. The row is the
 * thing the FE drills into ("12 employees skipped — review") and the
 * thing {@code POST /retry-skips} iterates over. Belongs to the compute
 * attempt: dropped on {@code /reset}, deleted-on-cascade if the run
 * itself is deleted, or removed individually when retry succeeds.
 *
 * <p>Not an {@link com.forward.hrms.commons.core.jpa.AuditedEntity} —
 * it's a system-side log of the just-completed batch, not a tracked
 * business record. The audit row written by {@code run_computed} /
 * {@code run_skips_retried} is the durable trail.
 */
@Entity
@Table(name = "payroll_run_skips")
@Getter
@Setter
public class PayrollRunSkip extends BaseLongIdEntity {

    @Column(name = "payroll_run_id", nullable = false)
    private Long payrollRunId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "employee_code", nullable = false, length = 32)
    private String employeeCode;

    @Column(name = "employee_full_name", nullable = false, length = 255)
    private String employeeFullName;

    @Column(name = "error_code", nullable = false, length = 64)
    private String errorCode;

    @Column(name = "message", nullable = false, length = 1000)
    private String message;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
