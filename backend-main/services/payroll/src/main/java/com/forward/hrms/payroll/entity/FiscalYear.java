package com.forward.hrms.payroll.entity;

import com.forward.hrms.payroll.enums.FiscalYearStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import org.hibernate.annotations.ColumnTransformer;

import java.time.LocalDateTime;
import java.time.LocalDate;

/**
 * Pakistan fiscal year. PK is the human-readable {@code code}
 * (e.g. {@code "2025-26"}) so we manage the audit columns ourselves
 * rather than extending {@link com.forward.hrms.commons.core.jpa.AuditedEntity}
 * (which assumes a {@code Long} surrogate id).
 */
@Entity
@Table(name = "fiscal_years")
@Getter
@Setter
public class FiscalYear {

    @Id
    @Column(name = "code", length = 7, nullable = false)
    private String code;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private FiscalYearStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "created_by", nullable = false, updatable = false, length = 64)
    private String createdBy;

    @Column(name = "updated_by", nullable = false, length = 64)
    private String updatedBy;

    @Column(name = "ip_address", columnDefinition = "inet")
    @ColumnTransformer(write = "?::inet")
    private String ipAddress;
}
