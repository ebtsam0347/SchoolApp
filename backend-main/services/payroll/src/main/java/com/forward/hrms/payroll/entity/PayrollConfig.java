package com.forward.hrms.payroll.entity;

import com.forward.hrms.commons.core.jpa.AuditedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * System-level payroll configuration. Effective-dated so we can change
 * rates without rewriting history. Holds statutory rates (EOBI / PESSI),
 * gratuity thresholds, and the canteen meal prices.
 */
@Entity
@Table(name = "payroll_config")
@Getter
@Setter
public class PayrollConfig extends AuditedEntity {

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "eobi_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal eobiAmount;

    @Column(name = "pessi_rate_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal pessiRatePercent;

    @Column(name = "pessi_salary_cap", nullable = false, precision = 15, scale = 2)
    private BigDecimal pessiSalaryCap;

    @Column(name = "gratuity_min_service_months", nullable = false)
    private int gratuityMinServiceMonths = 6;

    @Column(name = "gratuity_partial_year_threshold_months", nullable = false)
    private int gratuityPartialYearThresholdMonths = 6;

    @Column(name = "canteen_per_day_rate_management", nullable = false, precision = 15, scale = 2)
    private BigDecimal canteenPerDayRateManagement;

    @Column(name = "canteen_per_day_rate_other", nullable = false, precision = 15, scale = 2)
    private BigDecimal canteenPerDayRateOther;

    @Column(name = "notes")
    private String notes;
}
