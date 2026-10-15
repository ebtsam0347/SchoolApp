package com.forward.hrms.payroll.entity;

import com.forward.hrms.commons.core.jpa.AuditedEntity;

import com.forward.hrms.payroll.enums.BonusPaymentStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Per-(event, employee) frozen snapshot, written when the event is
 * activated.
 */
@Entity
@Table(name = "bonus_event_amounts")
@Getter
@Setter
public class BonusEventAmount extends AuditedEntity {

    @Column(name = "bonus_event_id", nullable = false)
    private Long bonusEventId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "hire_date", nullable = false)
    private LocalDate hireDate;

    @Column(name = "service_days", nullable = false)
    private int serviceDays;

    @Column(name = "basic_snapshot", nullable = false, precision = 15, scale = 2)
    private BigDecimal basicSnapshot;

    @Column(name = "full_bonus", nullable = false, precision = 15, scale = 2)
    private BigDecimal fullBonus;

    @Column(name = "paid_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 20)
    private BonusPaymentStatus paymentStatus = BonusPaymentStatus.PENDING;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;
}
