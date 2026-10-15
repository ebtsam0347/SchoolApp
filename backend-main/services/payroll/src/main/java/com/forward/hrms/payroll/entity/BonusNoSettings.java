package com.forward.hrms.payroll.entity;

import com.forward.hrms.commons.core.jpa.AuditedEntity;
import com.forward.hrms.payroll.enums.BonusNoSettingsStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "bonus_no_settings")
@Getter
@Setter
public class BonusNoSettings extends AuditedEntity {

    @Column(name = "fiscal_year_code", nullable = false, length = 7)
    private String fiscalYearCode;

    @Column(name = "bonus_no", nullable = false)
    private int bonusNo;

    @Column(name = "percentage_of_salary", nullable = false, precision = 5, scale = 2)
    private BigDecimal percentageOfSalary;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private BonusNoSettingsStatus status = BonusNoSettingsStatus.DRAFT;
}
