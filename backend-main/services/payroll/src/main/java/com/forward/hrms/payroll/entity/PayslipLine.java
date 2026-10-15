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

@Entity
@Table(name = "payslip_lines")
@Getter
@Setter
public class PayslipLine extends AuditedEntity {

    @Column(name = "payslip_id", nullable = false)
    private Long payslipId;

    @Column(name = "component_id")
    private Long componentId;

    @Column(name = "code", nullable = false, length = 32)
    private String code;

    @Column(name = "name", nullable = false, length = 64)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 16)
    private ComponentType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "calc_type", nullable = false, length = 16)
    private CalcType calcType = CalcType.FLAT;

    @Column(name = "rate", precision = 15, scale = 2)
    private BigDecimal rate;

    @Column(name = "days", precision = 5, scale = 2)
    private BigDecimal days;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;
}
