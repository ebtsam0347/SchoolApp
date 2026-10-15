package com.forward.hrms.payroll.settlement.context;

import com.forward.hrms.employee.enums.GradeType;
import com.forward.hrms.payroll.entity.EmployeePayrollSettings;
import com.forward.hrms.payroll.entity.EmployeeSalary;
import com.forward.hrms.payroll.entity.PayrollConfig;
import com.forward.hrms.payroll.enums.ForcedTerminationDeductionPercentage;
import com.forward.hrms.payroll.enums.NoticePayPercentage;
import com.forward.hrms.payroll.enums.SettlementAdjustmentType;
import com.forward.hrms.payroll.service.AttendanceSnapshot;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDate;

@NoArgsConstructor
@Getter
@Setter
@ToString
public class SettlementCalculationContext {

    private long employeeId;

    private String employeeCode;

    private LocalDate lastWorkingDay;

    private EmployeeSalary salary;

    private EmployeePayrollSettings payrollSettings;

    private PayrollConfig payrollConfig;

    private SettlementAdjustmentType adjustmentType;

    private NoticePayPercentage noticePayPercentage;

    private ForcedTerminationDeductionPercentage forcedDeductionPercentage;

    /**
     * Attendance snapshot for the settlement period (1st of the
     * termination month through the last working day). Drives the
     * LOP-aware basic/allowance proration in
     * {@code SettlementSalaryCalculator} — without it, earned salary
     * was computed purely off the calendar day-of-month, paying full
     * salary for a period the employee was entirely absent for.
     */
    private AttendanceSnapshot attendanceSnapshot;

    /**
     * Paid-leave day count for the settlement period, sourced from
     * attendance-service's termination-summary
     * {@code AttendanceSummaryDto.paidLeaveDays()} (attendance owns the
     * paid/unpaid leave classification). {@code SettlementSalaryCalculator}
     * adds this to present/half-day attendance when computing payable
     * attendance days — unpaid leave is deliberately excluded and must
     * never be added here.
     */
    private BigDecimal paidLeaveDays = BigDecimal.ZERO;

    /**
     * Monthly basic salaries granted as bonus per full fiscal year, picked
     * by HR on the termination screen (0.5, 1, 1.5, 2, ...). The system
     * accrues one day's share of a single basic salary and multiplies it
     * by this rate — see {@code SettlementBonusCalculator}. Absent means
     * one basic per year.
     */
    private BigDecimal bonusRate = BigDecimal.TWO;

    /**
     * Employee's grade type, used by {@code SettlementCanteenCalculator}
     * to pick the management vs. supervisor/labour canteen rate. Missing
     * grade defaults to LABOUR at the point of use (safer to under- than
     * over-charge).
     */
    private GradeType employeeGradeType;

}
