package com.forward.hrms.payroll.entity;

import com.forward.hrms.commons.core.jpa.AuditedEntity;
import com.forward.hrms.payroll.enums.BonusEventStatus;
import com.forward.hrms.payroll.enums.BonusEventType;

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
@Table(name = "bonus_events")
@Getter
@Setter
public class BonusEvent extends AuditedEntity {

    @Column(name = "fiscal_year_code", nullable = false, length = 7)
    private String fiscalYearCode;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 32)
    private BonusEventType eventType;

    @Column(name = "reference_date", nullable = false)
    private LocalDate referenceDate;

    @Column(name = "full_service_days_threshold", nullable = false)
    private int fullServiceDaysThreshold = 365;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private BonusEventStatus status = BonusEventStatus.DRAFT;

    @Column(name = "notes")
    private String notes;

    @Column(name = "activated_at")
    private LocalDateTime activatedAt;

    @Column(name = "activated_by")
    private Long activatedBy;
}
