package com.forward.hrms.employee.entity;

import com.forward.hrms.commons.core.jpa.AuditedEntity;
import com.forward.hrms.employee.enums.ExitStatus;
import com.forward.hrms.employee.enums.ExitType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.LocalDate;

@Entity
@Table(name = "employee_exits")
@Getter
@Setter
public class EmployeeExit extends AuditedEntity {

    /*
     * No longer unique — this table is historical (one row per exit
     * attempt). See V0621: an employee may have multiple rows across
     * their lifetime (resign/withdraw cycles, or a rejected/canceled
     * settlement followed by a new one), but at most one PENDING row
     * at a time (DB-enforced by uq_employee_exits_employee_pending)
     * and at most one row per non-null settlement_id (DB-enforced by
     * uq_employee_exits_settlement_id).
     */
    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "exit_type", nullable = false, length = 16)
    private ExitType exitType;

    @Column(name = "notice_start_date")
    private LocalDate noticeStartDate;

    @Column(name = "last_working_day", nullable = false)
    private LocalDate lastWorkingDay;

    @Column(name = "reason", length = 64)
    private String reason;

    @Column(name = "notes")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ExitStatus status;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "withdrawn_at")
    private LocalDateTime withdrawnAt;

    @Column(name = "withdrawn_reason")
    private String withdrawnReason;

    @Column(name = "settlement_id")
    private Long settlementId;

    /**
     * Employment status the employee had immediately before termination —
     * captured in {@code ExitService.createPendingExit} so
     * {@code TerminationSaga.undoTermination} can restore the exact prior
     * state instead of assuming ACTIVE.
     */
    @Column(name = "previous_employment_status", length = 20)
    private String previousEmploymentStatus;
}
