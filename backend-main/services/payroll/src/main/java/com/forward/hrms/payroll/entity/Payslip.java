package com.forward.hrms.payroll.entity;

import com.forward.hrms.commons.core.jpa.AuditedEntity;
import com.forward.hrms.payroll.enums.PayslipStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payslips")
@Getter
@Setter
public class Payslip extends AuditedEntity {

    @Column(name = "payroll_run_id", nullable = false)
    private Long payrollRunId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "employee_code", nullable = false, length = 32)
    private String employeeCode;

    @Column(name = "period_year", nullable = false)
    private int periodYear;

    @Column(name = "period_month", nullable = false)
    private int periodMonth;

    @Column(name = "fiscal_year_code", nullable = false, length = 7)
    private String fiscalYearCode;

    @Column(name = "basic", nullable = false, precision = 15, scale = 2)
    private BigDecimal basic = BigDecimal.ZERO;

    @Column(name = "allowances", nullable = false, precision = 15, scale = 2)
    private BigDecimal allowances = BigDecimal.ZERO;

    @Column(name = "gross", nullable = false, precision = 15, scale = 2)
    private BigDecimal gross = BigDecimal.ZERO;

    @Column(name = "deductions", nullable = false, precision = 15, scale = 2)
    private BigDecimal deductions = BigDecimal.ZERO;

    @Column(name = "tax", nullable = false, precision = 15, scale = 2)
    private BigDecimal tax = BigDecimal.ZERO;

    @Column(name = "eobi", nullable = false, precision = 15, scale = 2)
    private BigDecimal eobi = BigDecimal.ZERO;

    @Column(name = "pessi_employer", nullable = false, precision = 15, scale = 2)
    private BigDecimal pessiEmployer = BigDecimal.ZERO;

    @Column(name = "net", nullable = false, precision = 15, scale = 2)
    private BigDecimal net = BigDecimal.ZERO;

    @Column(name = "lop_days", nullable = false, precision = 5, scale = 2)
    private BigDecimal lopDays = BigDecimal.ZERO;

    @Column(name = "working_days")
    private Integer workingDays;

    @Column(name = "paid_days", precision = 5, scale = 2)
    private BigDecimal paidDays;

    @Column(name = "absent_days", precision = 5, scale = 2)
    private BigDecimal absentDays;

    @Column(name = "holidays_in_period")
    private Integer holidaysInPeriod;

    @Column(name = "weekends_in_period")
    private Integer weekendsInPeriod;

    @Column(name = "ot_hours", nullable = false, precision = 5, scale = 2)
    private BigDecimal otHours = BigDecimal.ZERO;

    @Column(name = "ot_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal otAmount = BigDecimal.ZERO;

    @Column(name = "pdf_object_key", length = 512)
    private String pdfObjectKey;

    @Column(name = "generated_at")
    private LocalDateTime generatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PayslipStatus status = PayslipStatus.DRAFT;

    @Column(name = "held_reason")
    private String heldReason;

    @Column(name = "released_in_run_id")
    private Long releasedInRunId;
}
