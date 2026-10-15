package com.forward.hrms.payroll.dto.response;

import com.forward.hrms.employee.dto.response.BelongingClearanceCheckDto;
import com.forward.hrms.employee.enums.GradeType;
import com.forward.hrms.payroll.client.dto.attendace.AttendanceSummaryDto;
import com.forward.hrms.payroll.enums.BonusEventType;
import com.forward.hrms.payroll.enums.ForcedTerminationDeductionPercentage;
import com.forward.hrms.payroll.enums.NoticePayPercentage;
import com.forward.hrms.payroll.enums.SettlementAdjustmentType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Schema(name = "SettlementPreviewDto",
        description = "Read-only simulation. Same shape as the eventual termination payslip.")
public record SettlementPreviewDto(long employeeId, LocalDate lastWorkingDay, EmployeeInfo employee,
                                   EarnedSalary earnedSalary, Gratuity gratuity, Advances advances,
                                   PermanentAdvances permanentAdvances, Bonus bonus,
                                   TerminationLeaveSummaryDto annualLeave,
                                   CombinedLeaveSummaryDto medicalAndCasualLeave,
                                   ExcessiveLeaveSummary annualExcessiveLeave,
                                   ExcessiveLeaveSummary medicalAndCasualExcessiveLeave,
                                   AttendanceSummarySnapshot attendanceSummary, FairPriceShop fairPriceShop,
                                   Canteen canteen,
                                   @Schema(description = "Company belongings still charged to the employee, "
                                           + "sourced from employee-service. HR must get `cleared` to true "
                                           + "before `POST /settlement/employees/{id}/hr-approval-confirmation` "
                                           + "will go through — the approval re-runs this same check and "
                                           + "answers 409 BELONGING_CLEARANCE_PENDING while anything is "
                                           + "outstanding. Carries no money: nothing here is deducted from "
                                           + "the settlement.")
                                   BelongingClearanceCheckDto belongings,
                                   TerminationPolicy terminationPolicy, List<OutstandingDeduction> outstandingDeductions,
                                   BigDecimal grossSettlement, BigDecimal totalDeductions, BigDecimal tax,
                                   BigDecimal net
) {
    @Schema(name = "SettlementPreviewDto.EarnedSalary")
    public record EarnedSalary(BigDecimal monthlyBasicSalary, BigDecimal earnedBasic, BigDecimal allowances,
                               BigDecimal gross) {
    }

    @Schema(name = "SettlementPreviewDto.Gratuity")
    public record Gratuity(boolean eligible, int completedYears, BigDecimal amount) {
    }

    @Schema(name = "SettlementPreviewDto.OutstandingDeduction")
    public record OutstandingDeduction(String code, BigDecimal amount) {
    }

    @Schema(name = "SettlementPreviewDto.Advances")
    public record Advances(BigDecimal permanent, BigDecimal shortTerm, BigDecimal rfd, BigDecimal total) {
    }

    @Schema(name = "SettlementPreviewDto.PermanentAdvances")
    public record PermanentAdvances(
            List<PermanentAdvance> advances,
            BigDecimal totalOutstanding
    ) {
    }

    @Schema(name = "SettlementPreviewDto.PermanentAdvance")
    public record PermanentAdvance(Long advanceId, String requestCode, LocalDate receivingDate,
                                   BigDecimal originalAmount,
                                   BigDecimal recoveredAmount, BigDecimal outstandingAmount,
                                   BigDecimal monthlyDeduction,
                                   BigDecimal bonusOffset, BigDecimal annualLeaveOffset) {
    }

    @Schema(name = "SettlementPreviewDto.Bonus")
    public record Bonus(String name, BigDecimal outstanding, TerminationBonus terminationBonus,
                        List<OutstandingBonus> events) {
    }

    @Schema(name = "SettlementPreviewDto.TerminationBonus",
            description = "Termination-time bonus accrual, and the full working behind it so HR can "
                    + "verify the figure line by line. `amount` is what flows into the gross settlement "
                    + "(it is the same value as `Bonus.outstanding`). Accrual: "
                    + "`monthlyBasic / 365 * (bonusDays + 1) * bonusRate`, earned only once the employee "
                    + "has completed at least 90 days of service; the bonus already released for this "
                    + "same fiscal year (Eid installments) is then netted off.")
    public record TerminationBonus(
            @Schema(description = "Date of joining the accrual is counted from.")
            LocalDate hireDate,
            @Schema(description = "1 July of the fiscal year the last working day falls in.")
            LocalDate fiscalYearStart,
            @Schema(description = "30 June closing the same fiscal year — the window the already-paid "
                    + "bonus is summed over.")
            LocalDate fiscalYearEnd,
            @Schema(description = "Last working day — the end of both day counts.")
            LocalDate lastWorkingDay,
            @Schema(description = "Monthly basic salary the accrual is priced off.")
            BigDecimal monthlyBasic,
            @Schema(description = "Monthly basic salaries granted as bonus per full year, chosen by HR "
                    + "(0.5, 1, 1.5, 2, ...).")
            BigDecimal bonusRate,
            @Schema(description = "since_doj — plain day difference between hireDate and lastWorkingDay.")
            long serviceDays,
            @Schema(description = "since_fy — plain day difference between fiscalYearStart and lastWorkingDay.")
            long fiscalYearDays,
            @Schema(description = "min(serviceDays, fiscalYearDays).")
            long bonusDays,
            @Schema(description = "bonusDays + 1 — both the first and the last day are paid.")
            long payableDays,
            @Schema(description = "One day's share of a single basic salary (monthlyBasic / 365), shown "
                    + "for verification only; the amount itself is computed without rounding this.")
            BigDecimal perDayBonus,
            @Schema(description = "False when serviceDays < 90 — then the accrual is zero.")
            boolean eligible,
            @Schema(description = "Bonus earned for this fiscal year, rounded to the nearest rupee, "
                    + "before anything already paid is netted off.")
            BigDecimal accruedAmount,
            @Schema(description = "Bonus already released to this employee for this same fiscal year, "
                    + "across both paths: approved bonus runs (`bonus_transaction`, status PAID or "
                    + "RELEASED) plus PAID Eid bonus-event installments. Netted off the accrual so a "
                    + "mid-year bonus is not paid twice.")
            BigDecimal alreadyPaidAmount,
            @Schema(description = "Bonus payable at termination: accruedAmount - alreadyPaidAmount, "
                    + "never below zero (an over-payment is not clawed back here).")
            BigDecimal amount) {
    }

    @Schema(name = "SettlementPreviewDto.OutstandingBonus")
    public record OutstandingBonus(Long bonusEventId, String eventName, BonusEventType eventType,
                                   LocalDate referenceDate,
                                   BigDecimal eligibleAmount, BigDecimal paidAmount, BigDecimal outstandingAmount) {
    }

    // personal info
    @Schema(name = "SettlementPreviewDto.EmployeeInfo")
    public record EmployeeInfo(Long employeeId, String employeeCode, String employeeName, String department,
                               String section, String designation, String grade, GradeType gradeType,
                               LocalDate hireDate, LocalDate gratuityStartDate) {
    }

    @Schema(name = "SettlementPreviewDto.FairPriceShop")
    public record FairPriceShop(BigDecimal outstandingAmount) {
    }

    @Schema(name = "SettlementPreviewDto.Canteen",
            description = "Grade-conditional canteen deduction for the settlement period (1st of the " +
                    "termination month through the last working day). Mirrors the regular payroll canteen rule.")
    public record Canteen(BigDecimal amount) {
    }

    @Schema(name = "SettlementPreviewDto.TerminationPolicy")
    public record TerminationPolicy(SettlementAdjustmentType adjustmentType,
                                    NoticePayPercentage noticePayPercentage,
                                    BigDecimal noticePayAmount,
                                    ForcedTerminationDeductionPercentage forcedDeductionPercentage,
                                    BigDecimal forcedDeductionAmount) {

    }

    @Schema(name = "SettlementPreviewDto.ExcessiveLeaveSummary",
            description = "Leave days used beyond what was earned, valued in money, deducted from the settlement.")
    public record ExcessiveLeaveSummary(BigDecimal excessiveDays, BigDecimal excessiveAmount) {
    }

    @Schema(name = "SettlementPreviewDto.AttendanceSummarySnapshot",
            description = "Attendance/overtime snapshot for the settlement period, sourced from attendance-service.")
    public record AttendanceSummarySnapshot(Long employeeId, LocalDate from, LocalDate to,
                                            AttendanceSummaryDto attendance, NormalOvertimeSummary normalOvertime) {
    }

    @Schema(name = "SettlementPreviewDto.NormalOvertimeSummary",
            description = "Normal overtime only — Extra/Special overtime are excluded from the " +
                    "termination settlement snapshot.")
    public record NormalOvertimeSummary(NormalOvertimeCategory approved, NormalOvertimeCategory pending,
                                        NormalOvertimeCategory unapplied, NormalOvertimeCategory total) {
    }

    @Schema(name = "SettlementPreviewDto.NormalOvertimeCategory",
            description = "Normal OT minutes/hours from attendance-service, valued at 2x the employee's " +
                    "basic hourly rate (monthlyBasic / 26 / 8 * 2).")
    public record NormalOvertimeCategory(int minutes, String hours, BigDecimal amount) {
    }
}
