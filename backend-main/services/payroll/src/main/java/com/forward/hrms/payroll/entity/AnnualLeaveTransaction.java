package com.forward.hrms.payroll.entity;

import com.forward.hrms.commons.core.jpa.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "annual_leave_transaction")
@Getter
@Setter
public class AnnualLeaveTransaction extends AuditedEntity {

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "employee_code", nullable = false, length = 32)
    private String employeeCode;

    @Column(name = "calculation_month", nullable = false)
    private LocalDate calculationMonth;

    @Column(name = "basic_salary", nullable = false)
    private BigDecimal basicSalary;

    @Column(name = "per_day_salary", nullable = false)
    private BigDecimal perDaySalary;

    @Column(name = "cycle_start", nullable = false)
    private LocalDate cycleStart;

    @Column(name = "cycle_end", nullable = false)
    private LocalDate cycleEnd;

    @Column(name = "eligible_leave_days", nullable = false)
    private Long eligibleLeaveDays;

    @Column(name = "availed_leave_days")
    private Long availedLeaveDays = 0L;

    /**
     * eligible_leave_days - availed_leave_days
     */
    @Column(name = "payable_leave_days", nullable = false)
    private Long payableLeaveDays;

    /**
     * (basic salary / 26) * annual leave quota paid days
     */
    @Column(name = "leave_amount", nullable = false)
    private BigDecimal leaveAmount;

    /**
     * payable_leave_days * per_day_salary
     */
    @Column(name = "gross_annual_leave_amount", nullable = false)
    private BigDecimal grossAnnualLeaveAmount;

    @Column(name = "deduction_as_long_term_advance")
    private BigDecimal deductionAsLongTermAdvance = BigDecimal.ZERO;

    /**
     * gross_annual_leave_amount - deduction_as_long_term_advance
     */
    @Column(name = "net_amount", nullable = false)
    private BigDecimal netAmount;

    @Column(name = "payment_status")
    private Boolean paymentStatus;

    @Column(name = "lock_status")
    private Boolean lockStatus;

    @Column(name = "joining_date", nullable = false)
    private LocalDate joiningDate;


}
