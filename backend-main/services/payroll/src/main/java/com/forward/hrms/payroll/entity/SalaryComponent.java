package com.forward.hrms.payroll.entity;

import com.forward.hrms.commons.core.jpa.AuditedEntity;
import com.forward.hrms.payroll.enums.CalcType;
import com.forward.hrms.payroll.enums.ComponentType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Catalog row. Each business entity (allowance / deduction) is one row;
 * employees pick from this catalog and supply an amount on
 * {@link EmployeeSalaryComponent}.
 */
@Entity
@Table(name = "salary_components")
@Getter
@Setter
public class SalaryComponent extends AuditedEntity {

    @Column(name = "code", nullable = false, length = 32, unique = true)
    private String code;

    @Column(name = "name", nullable = false, length = 64)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 16)
    private ComponentType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "calc_type", nullable = false, length = 16)
    private CalcType calcType = CalcType.FLAT;

    @Column(name = "default_amount", precision = 15, scale = 2)
    private BigDecimal defaultAmount;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name  = "calculate_with_salary", nullable = false)
    private boolean calculateWithSalary;
}
