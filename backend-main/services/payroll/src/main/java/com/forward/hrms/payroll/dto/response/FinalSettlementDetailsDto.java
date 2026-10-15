package com.forward.hrms.payroll.dto.response;


import com.forward.hrms.employee.enums.GradeType;
import com.forward.hrms.payroll.enums.AdvanceStatus;
import com.forward.hrms.payroll.enums.BonusEventType;
import com.forward.hrms.payroll.enums.EmployeeFinalSettlementStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record FinalSettlementDetailsDto(

        Long settlementId,

        EmployeeFinalSettlementStatus status,

        LocalDate lastWorkingDay,

        String remarks,

        EmployeeInfo employee,

        EarnedSalary earnedSalary,

        Gratuity gratuity,

        Advances advances,

        PermanentAdvances permanentAdvances,

        Bonus bonus,

        LeaveSummary annualLeave,

        CombinedLeaveSummary medicalAndCasualLeave,

        ExcessiveLeaveSummary annualExcessiveLeave,

        ExcessiveLeaveSummary medicalAndCasualExcessiveLeave,

        FairPriceShop fairPriceShop,

        Canteen canteen,

        List<OutstandingDeduction> outstandingDeductions,

        AttendanceDetail attendanceDetail,

        NormalOvertimeDetail normalOvertimeDetail,

        Totals totals,

        Audit audit

) {

    // ============================================================
    // Employee
    // ============================================================

    public record EmployeeInfo(

            Long employeeId,

            String employeeCode,

            String employeeName,

            String department,

            String section,

            String designation,

            String grade,

            GradeType gradeType,

            LocalDate hireDate,

            LocalDate gratuityStartDate

    ) {
    }

    // ============================================================
    // Salary
    // ============================================================

    public record EarnedSalary(

            BigDecimal monthlyBasicSalary,

            BigDecimal earnedBasicSalary,

            BigDecimal allowances,

            BigDecimal grossSalary

    ) {
    }

    // ============================================================
    // Gratuity
    // ============================================================

    public record Gratuity(

            boolean eligible,

            int completedYears,

            BigDecimal amount

    ) {
    }

    // ============================================================
    // Advances
    // ============================================================

    public record Advances(

            BigDecimal permanent,

            BigDecimal shortTerm,

            BigDecimal rfd,

            BigDecimal total

    ) {
    }

    // ============================================================
    // Permanent Advances
    // ============================================================

    public record PermanentAdvances(

            List<PermanentAdvance> advances,

            BigDecimal totalOutstanding

    ) {
    }

    public record PermanentAdvance(

            Long advanceId,

            String requestCode,

            LocalDate receivingDate,

            AdvanceStatus advanceStatus,

            String description,

            BigDecimal originalAmount,

            BigDecimal recoveredAmount,

            BigDecimal outstandingAmount,

            BigDecimal monthlyDeduction,

            BigDecimal bonusOffset,

            BigDecimal annualLeaveOffset,

            BigDecimal bonusRecovered,

            BigDecimal leaveRecovered,

            BigDecimal settlementRecovered

    ) {
    }

    // ============================================================
    // Bonus
    // ============================================================

    public record Bonus(

            BigDecimal outstanding,

            List<OutstandingBonus> events

    ) {
    }

    public record OutstandingBonus(

            Long bonusEventId,

            String eventName,

            BonusEventType eventType,

            LocalDate referenceDate,

            BigDecimal eligibleAmount,

            BigDecimal paidAmount,

            BigDecimal outstandingAmount

    ) {
    }

    // ============================================================
    // Leave
    // ============================================================

    public record LeaveSummary(

            Long leaveTypeId,

            String leaveCode,

            String leaveName,

            LocalDate cycleStart,

            LocalDate cycleEnd,

            BigDecimal allocated,

            BigDecimal accrued,

            BigDecimal used,

            BigDecimal pending,

            BigDecimal payableDays,

            BigDecimal excessiveDays,

            BigDecimal encashmentAmount,

            BigDecimal excessiveAmount

    ) {
    }

    /**
     * Medical (M1) + Casual leave, combined into one bucket. Neither
     * type is individually encashable, so encashmentAmount is always
     * zero here; only excessiveAmount (days used beyond what was
     * earned) applies.
     */
    public record CombinedLeaveSummary(

            BigDecimal allocated,

            BigDecimal earned,

            BigDecimal used,

            BigDecimal pending,

            BigDecimal payableDays,

            BigDecimal excessiveDays,

            BigDecimal encashmentAmount,

            BigDecimal excessiveAmount

    ) {
    }

    /**
     * Leave days used beyond what was earned, valued in money,
     * deducted from the settlement.
     */
    public record ExcessiveLeaveSummary(

            BigDecimal excessiveDays,

            BigDecimal excessiveAmount

    ) {
    }

    // ============================================================
    // Deductions
    // ============================================================

    public record OutstandingDeduction(

            String deductionCode,

            String deductionName,

            BigDecimal amount

    ) {
    }

    // ============================================================
    // Fair Price Shop
    // ============================================================

    public record FairPriceShop(

            BigDecimal outstandingAmount

    ) {
    }

    // ============================================================
    // Canteen
    // ============================================================

    public record Canteen(

            BigDecimal amount

    ) {
    }

    // ============================================================
    // Attendance Snapshot (historical — settlement creation only)
    // ============================================================

    public record AttendanceDetail(

            LocalDate periodFrom,

            LocalDate periodTo,

            Long presentDays,

            Long halfDays,

            Long leaveDays,

            Long paidLeaveDays,

            Long unpaidLeaveDays,

            Long absentDays,

            Long weekendDays,

            Long holidayDays

    ) {
    }

    // ============================================================
    // Normal Overtime Snapshot (historical — settlement creation only)
    // ============================================================

    public record NormalOvertimeDetail(

            Integer approvedMinutes,

            BigDecimal approvedAmount,

            Integer pendingMinutes,

            BigDecimal pendingAmount,

            Integer unappliedMinutes,

            BigDecimal unappliedAmount,

            Integer totalMinutes,

            BigDecimal totalAmount

    ) {
    }

    // ============================================================
    // Totals
    // ============================================================

    public record Totals(

            BigDecimal grossSettlement,

            BigDecimal totalDeductions,

            BigDecimal tax,

            BigDecimal netSettlement

    ) {
    }

    // ============================================================
    // Audit
    // ============================================================

    public record Audit(

            Long createdBy,

            LocalDateTime createdAt,

            Long approvedBy,

            LocalDateTime approvedAt,

            Long paidBy,

            LocalDateTime paidAt,

            LocalDateTime updatedAt

    ) {
    }

}
