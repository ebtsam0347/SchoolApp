package com.forward.hrms.employee.entity;

import com.forward.hrms.commons.core.jpa.AuditedEntity;
import com.forward.hrms.employee.enums.SuspensionStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "employee_suspensions")
@Getter
@Setter
public class EmployeeSuspension extends AuditedEntity {

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** Planned end; null = open-ended. */
    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "reason", nullable = false)
    private String reason;

    @Column(name = "with_pay", nullable = false)
    private boolean withPay;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private SuspensionStatus status = SuspensionStatus.ACTIVE;

    @Column(name = "lifted_on")
    private LocalDate liftedOn;

    @Column(name = "lifted_reason")
    private String liftedReason;
}
