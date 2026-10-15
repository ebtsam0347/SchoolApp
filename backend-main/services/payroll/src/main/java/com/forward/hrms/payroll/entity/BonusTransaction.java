package com.forward.hrms.payroll.entity;

import com.forward.hrms.commons.core.jpa.AuditedEntity;
import com.forward.hrms.payroll.enums.BonusTransactionPaymentMode;
import com.forward.hrms.payroll.enums.BonusTransactionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "bonus_transaction")
@Getter
@Setter
public class BonusTransaction extends AuditedEntity {

    @Column(name = "fiscal_year_code", nullable = false, length = 7)
    private String fiscalYearCode;

    @Column(name = "bonus_no", nullable = false)
    private int bonusNo;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "employee_code", nullable = false, length = 32)
    private String employeeCode;

    @Column(name = "date_of_joining", nullable = false)
    private LocalDate dateOfJoining;

    @Column(name = "basic_salary", nullable = false, precision = 15, scale = 2)
    private BigDecimal basicSalary = BigDecimal.ZERO;

    @Column(name = "no_of_months", precision = 5, scale = 2)
    private long noOfMonths = 0;

    @Column(name = "percentage_of_salary", nullable = false, precision = 5, scale = 2)
    private BigDecimal percentageOfSalary = BigDecimal.ZERO;

    @Column(name = "percentage_salary_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal percentageSalaryAmount = BigDecimal.ZERO;

    @Column(name = "no_of_days", nullable = false, precision = 5, scale = 2)
    private Long noOfDays = 0L;

    @Column(name = "deduction_as_long_term_advance", nullable = false, precision = 15, scale = 2)
    private BigDecimal deductionAsLongTermAdvance = BigDecimal.ZERO;

    @Column(name = "rfd_advance_added")
    private BigDecimal rfdAdvanceAdded;

    @Column(name = "net_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal netAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_mode", nullable = false, length = 20)
    private BonusTransactionPaymentMode bonusTransactionPaymentMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BonusTransactionStatus status = BonusTransactionStatus.DRAFT;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;
}
