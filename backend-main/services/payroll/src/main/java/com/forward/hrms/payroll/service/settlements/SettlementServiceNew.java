package com.forward.hrms.payroll.service.settlements;

import com.forward.hrms.payroll.client.AttendanceClient;
import com.forward.hrms.payroll.client.EmployeeClient;
import com.forward.hrms.payroll.client.dto.attendace.AttendanceSummaryDto;
import com.forward.hrms.payroll.client.dto.attendace.DurationDto;
import com.forward.hrms.payroll.client.dto.attendace.TerminationAttendanceSummaryDto;
import com.forward.hrms.employee.dto.response.BelongingClearanceCheckDto;
import com.forward.hrms.payroll.client.dto.employee.EmployeeTerminationInfo;
import com.forward.hrms.payroll.dto.response.SettlementPreviewDto;
import com.forward.hrms.payroll.dto.response.TerminationLeavesSummaryDto;
import com.forward.hrms.payroll.entity.EmployeePayrollSettings;
import com.forward.hrms.payroll.entity.EmployeeSalary;
import com.forward.hrms.payroll.enums.ForcedTerminationDeductionPercentage;
import com.forward.hrms.payroll.enums.NoticePayPercentage;
import com.forward.hrms.payroll.enums.SettlementAdjustmentType;
import com.forward.hrms.payroll.repository.projection.PermanentAdvanceSettlementProjection;
import com.forward.hrms.payroll.service.AttendanceSnapshot;
import com.forward.hrms.payroll.service.EmployeePayrollSettingsService;
import com.forward.hrms.payroll.service.EmployeeSalaryService;
import com.forward.hrms.payroll.service.PayrollConfigService;
import com.forward.hrms.payroll.settlement.context.SettlementCalculationContext;
import com.forward.hrms.payroll.settlement.context.SettlementCalculationResult;
import com.forward.hrms.payroll.settlement.engine.SettlementCalculationEngine;
import com.forward.hrms.commons.core.money.Money;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Read-only final-settlement preview. Distinct from the actual termination
 * payroll run that the daily termination job will create on
 * {@code employee.terminated} — no state change, no events emitted.
 */
@Service
@RequiredArgsConstructor
public class SettlementServiceNew {

    // 26 working days/month × 8 hours/day × 60 minutes/hour. Denominator
    // for the Normal OT amount formula — see computeNormalOvertimeAmount.
    private static final BigDecimal NORMAL_OT_MINUTES_DIVISOR = BigDecimal.valueOf(26L * 8 * 60);

    private final EmployeeSalaryService salary;
    private final SettlementCalculationEngine engine;
    private final EmployeePayrollSettingsService settings;
    private final PayrollConfigService payrollConfig;
    private final EmployeeClient employeeClient;
    private final AttendanceClient attendanceClient;

    @Transactional(readOnly = true)
    public SettlementPreviewDto previewNew(
            Long employeeId,
            LocalDate lastWorkingDay,
            SettlementAdjustmentType settlementAdjustmentType,
            NoticePayPercentage noticePayPercentage,
            ForcedTerminationDeductionPercentage forcedDeductionPercentage,
            BigDecimal bonusRate
    ) {

        LocalDate lwd = lastWorkingDay == null ? LocalDate.now() : lastWorkingDay;

        // get first of month
        LocalDate firstDayOfMonth = lwd.withDayOfMonth(1);

        // we used the method find currentEntity to get the latest effective on with null
        // instead of findEffectiveOn which was producing wrong results in our case.
        EmployeeSalary salaryRow = salary.currentEntity(employeeId);

        EmployeePayrollSettings payrollSettings = settings.load(employeeId);

        EmployeeTerminationInfo employee = employeeClient.terminationInfo(employeeId);

        TerminationAttendanceSummaryDto attendanceSummary = attendanceClient.terminationAttendanceSummary(
                employeeId,
                firstDayOfMonth,
                lwd
        );

        // Drives LOP-aware proration in SettlementSalaryCalculator — a
        // terminating employee absent for the whole period must not
        // still earn a calendar-day fraction of basic pay.
        //
        // Built from the termination-summary aggregate (already fetched
        // above) rather than a second attendanceClient.snapshot() call.
        // That daily-rows list query returns genuinely empty for periods
        // whose daily rows haven't been materialized yet by
        // NightlyCloseJob (e.g. the current, still-open day), which made
        // settlement preview hard-fail with ATTENDANCE_SNAPSHOT_FAILED
        // for perfectly normal terminations. The aggregate query behind
        // terminationAttendanceSummary uses COALESCE(SUM(...), 0) and
        // always returns a zero-filled row instead — the same tolerant
        // source the rest of this preview already depends on.
        AttendanceSnapshot attendanceSnapshot = toAttendanceSnapshot(attendanceSummary, firstDayOfMonth, lwd);

        SettlementCalculationContext ctx = new SettlementCalculationContext();

        ctx.setEmployeeId(employeeId);
        ctx.setEmployeeCode(employee.employeeCode());
        ctx.setLastWorkingDay(lwd);
        ctx.setSalary(salaryRow);
        ctx.setPayrollSettings(payrollSettings);
        ctx.setPayrollConfig(payrollConfig.effectiveOn(lwd));
        ctx.setAdjustmentType(settlementAdjustmentType);
        ctx.setNoticePayPercentage(noticePayPercentage);
        ctx.setForcedDeductionPercentage(forcedDeductionPercentage);
        ctx.setBonusRate(bonusRate == null ? BigDecimal.TWO : bonusRate);
        ctx.setAttendanceSnapshot(attendanceSnapshot);
        ctx.setPaidLeaveDays(BigDecimal.valueOf(attendanceSummary.attendance().paidLeaveDays()));
        ctx.setEmployeeGradeType(employee.gradeType());

        SettlementCalculationResult result = engine.calculate(ctx);

        // Belongings carry no money — they are a gate, not a deduction, so
        // they are fetched here for display rather than fed into the engine.
        // SettlementFinalizationService.hrApproval re-runs the same check
        // against live data at approval time; this copy is only what HR
        // looks at on the termination screen.
        BelongingClearanceCheckDto belongings = employeeClient.belongingClearance(employeeId);

        return buildPreview(
                employee,
                payrollSettings,
                lwd,
                result,
                attendanceSummary,
                belongings
        );
    }

    private SettlementPreviewDto buildPreview(
            EmployeeTerminationInfo employee,
            EmployeePayrollSettings payrollSettings,
            LocalDate lastWorkingDay,
            SettlementCalculationResult result,
            TerminationAttendanceSummaryDto attendanceSummary,
            BelongingClearanceCheckDto belongings) {

        SettlementPreviewDto.AttendanceSummarySnapshot attendanceSummarySnapshot =
                toAttendanceSummarySnapshot(attendanceSummary, result.getMonthlyBasicSalary());

        SettlementPreviewDto.EmployeeInfo employeeInfo = new SettlementPreviewDto.EmployeeInfo(

                employee.employeeId(),
                employee.employeeCode(),
                employee.employeeName(),
                employee.department(),
                employee.section(),
                employee.designation(),
                employee.grade(),
                employee.gradeType(),
                payrollSettings.getHireDate(),
                payrollSettings.getGratuityStartDate()
        );

        TerminationLeavesSummaryDto leavesSummary =
                result.getLeavesSummaries();

        return new SettlementPreviewDto(

                employee.employeeId(),

                lastWorkingDay,

                employeeInfo,

                new SettlementPreviewDto.EarnedSalary(

                        result.getMonthlyBasicSalary(),
                        result.getBasicEarnedSalary(),
                        result.getAllowances(),
                        result.getGrossSalary()
                ),

                new SettlementPreviewDto.Gratuity(

                        result.isGratuityEligible(),
                        result.getGratuityYears(),
                        result.getGratuity()
                ),

                new SettlementPreviewDto.Advances(

                        result.getPermanentAdvanceTotal(),
                        result.getShortTermAdvance(),
                        result.getRfdAdvanceAmount(),
                        result.getPermanentAdvanceTotal()
                                .add(result.getShortTermAdvance())
                                .add(result.getRfdAdvanceAmount())
                ),

                buildPermanentAdvances(result)

                ,

                new SettlementPreviewDto.Bonus(

                        "outstandingBonus",

                        result.getOutstandingBonus(),

                        result.getTerminationBonus(),

                        result.getBonuses()
                ),

                leavesSummary != null ? leavesSummary.annualLeave() : null,

                leavesSummary != null ? leavesSummary.medicalAndCasualLeave() : null,

                new SettlementPreviewDto.ExcessiveLeaveSummary(
                        result.getAnnualExcessiveDays(),
                        result.getAnnualExcessiveAmount()
                ),

                new SettlementPreviewDto.ExcessiveLeaveSummary(
                        result.getMedicalCasualExcessiveDays(),
                        result.getMedicalCasualExcessiveAmount()
                ),

                attendanceSummarySnapshot,

                new SettlementPreviewDto.FairPriceShop(

                        result.getFairPriceShopAmount()
                ),

                new SettlementPreviewDto.Canteen(

                        result.getCanteenAmount()
                ),

                belongings,

                new SettlementPreviewDto.TerminationPolicy(

                        result.getAdjustmentType(),
                        result.getNoticePayPercentage(),
                        result.getNoticePayAmount(),
                        result.getForcedDeductionPercentage(),
                        result.getForcedDeductionAmount()
                ),

                result.getDeductions(),

                result.getGrossSettlement(),

                result.getTotalDeductions(),

                result.getIncomeTax(),

                result.getNetSettlement()
        );
    }



    private SettlementPreviewDto.PermanentAdvances buildPermanentAdvances(
            SettlementCalculationResult result) {

        List<SettlementPreviewDto.PermanentAdvance> advances =
                result.getPermanentAdvances()
                        .stream()
                        .map(this::toPermanentAdvance)
                        .toList();

        return new SettlementPreviewDto.PermanentAdvances(

                advances,

                result.getPermanentAdvanceTotal()
        );
    }

    /**
     * Converts attendance-service's raw termination-summary snapshot
     * (Normal OT minutes/hours only — Extra/Special are excluded upstream)
     * into the payroll-owned response shape, adding the Normal OT money
     * amount. This is the only place Normal OT money is computed;
     * attendance-service has no visibility into basic salary.
     */
    private SettlementPreviewDto.AttendanceSummarySnapshot toAttendanceSummarySnapshot(
            TerminationAttendanceSummaryDto summary,
            BigDecimal monthlyBasicSalary) {

        SettlementPreviewDto.NormalOvertimeCategory approved =
                toOvertimeCategory(summary.normalOvertime().approved(), monthlyBasicSalary);
        SettlementPreviewDto.NormalOvertimeCategory pending =
                toOvertimeCategory(summary.normalOvertime().pending(), monthlyBasicSalary);
        SettlementPreviewDto.NormalOvertimeCategory unapplied =
                toOvertimeCategory(summary.normalOvertime().unapplied(), monthlyBasicSalary);

        DurationDto totalDuration = summary.normalOvertime().total();
        BigDecimal totalAmount = approved.amount().add(pending.amount()).add(unapplied.amount());
        SettlementPreviewDto.NormalOvertimeCategory total = new SettlementPreviewDto.NormalOvertimeCategory(
                totalDuration == null ? 0 : totalDuration.minutes(),
                totalDuration == null ? "0h 0m" : totalDuration.hours(),
                totalAmount
        );

        return new SettlementPreviewDto.AttendanceSummarySnapshot(
                summary.employeeId(),
                summary.from(),
                summary.to(),
                summary.attendance(),
                new SettlementPreviewDto.NormalOvertimeSummary(approved, pending, unapplied, total)
        );
    }

    private SettlementPreviewDto.NormalOvertimeCategory toOvertimeCategory(
            DurationDto duration,
            BigDecimal monthlyBasicSalary) {

        int minutes = duration == null ? 0 : duration.minutes();
        String hours = duration == null ? "0h 0m" : duration.hours();
        return new SettlementPreviewDto.NormalOvertimeCategory(
                minutes, hours, computeNormalOvertimeAmount(minutes, monthlyBasicSalary));
    }

    /**
     * Normal OT is paid at 2x the employee's basic hourly rate:
     * {@code dailyBasic = monthlyBasic / 26}, {@code hourlyBasic = dailyBasic / 8},
     * {@code normalOtHourlyRate = hourlyBasic * 2}. Equivalently
     * {@code amount = minutes * monthlyBasic * 2 / (26 * 8 * 60)}, computed
     * from the whole minute count (not just complete hours) so 90 minutes
     * prices as 1.5 hours, not 1. The intermediate division keeps 10dp
     * precision; {@link Money} applies the project's CEILING/scale-2
     * rounding only once, on the final amount.
     */
    private BigDecimal computeNormalOvertimeAmount(int minutes, BigDecimal monthlyBasicSalary) {
        if (minutes <= 0 || monthlyBasicSalary == null || monthlyBasicSalary.signum() == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal raw = BigDecimal.valueOf(minutes)
                .multiply(monthlyBasicSalary)
                .multiply(BigDecimal.valueOf(2))
                .divide(NORMAL_OT_MINUTES_DIVISOR, 10, RoundingMode.CEILING);
        return Money.of(raw).amount();
    }

    /**
     * Builds the {@link AttendanceSnapshot} that drives LOP-aware salary
     * proration from the termination-summary aggregate rather than a
     * separate attendanceClient.snapshot() call. See the comment at the
     * call site in {@link #previewNew} for why.
     *
     * <p>Paid days are built up explicitly from what attendance actually
     * reports as present/leave/holiday/weekend, rather than assuming
     * every calendar day the aggregate doesn't call out as ABSENT is
     * paid. That "calendar days minus explicit absences" shape used to
     * be here, and it silently paid full salary for days that simply
     * have no materialized attendance row yet (e.g. the still-open
     * termination month before {@code NightlyCloseJob} has run) —
     * absentDays reads 0 for those days, not because the employee was
     * present, but because nothing classified them at all. Summing the
     * known-paid buckets instead means any unaccounted day (no punch,
     * no leave, not a holiday/weekend) falls out as unpaid by
     * construction, matching what attendanceSummary actually shows for
     * presentDays.
     *
     * <p>The aggregate now distinguishes paid vs. unpaid leave — only
     * {@code paidLeaveDays} counts toward payable/earned-salary days;
     * {@code unpaidLeaveDays} contributes nothing (matches attendance's
     * authoritative LEAVE-record {@code paid} classification, not a
     * separately invented rule here).
     */
    private AttendanceSnapshot toAttendanceSnapshot(
            TerminationAttendanceSummaryDto summary,
            LocalDate periodStart,
            LocalDate periodEnd) {

        AttendanceSummaryDto a = summary.attendance();

        int daysInPeriod = (int) ChronoUnit.DAYS.between(periodStart, periodEnd) + 1;
        BigDecimal daysInPeriodDecimal = BigDecimal.valueOf(daysInPeriod);

        BigDecimal halfDayFraction = BigDecimal.valueOf(a.halfDays()).divide(BigDecimal.valueOf(2));
        BigDecimal daysPresent = BigDecimal.valueOf(a.presentDays()).add(halfDayFraction);
        BigDecimal absentDays = BigDecimal.valueOf(a.absentDays()).add(halfDayFraction);

        int workingDays = Math.max(0, daysInPeriod - (int) a.holidayDays() - (int) a.weekendDays());

        // Explicitly-accounted-paid days: present + paid leave + holidays
        // + weekends. Unpaid leave contributes nothing. Anything not in
        // this sum (unpunched/unclassified days) is treated as unpaid,
        // capped at the period length.
        BigDecimal accountedPaidDays = daysPresent
                .add(BigDecimal.valueOf(a.paidLeaveDays()))
                .add(BigDecimal.valueOf(a.holidayDays()))
                .add(BigDecimal.valueOf(a.weekendDays()));
        BigDecimal paidDays = accountedPaidDays.min(daysInPeriodDecimal);
        if (paidDays.signum() < 0) paidDays = BigDecimal.ZERO;

        BigDecimal lopDays = daysInPeriodDecimal.subtract(paidDays);
        if (lopDays.signum() < 0) lopDays = BigDecimal.ZERO;

        return new AttendanceSnapshot(
                lopDays,
                daysPresent,
                BigDecimal.ZERO,
                workingDays,
                paidDays,
                absentDays,
                (int) a.holidayDays(),
                (int) a.weekendDays()
        );
    }

    private SettlementPreviewDto.PermanentAdvance toPermanentAdvance(
            PermanentAdvanceSettlementProjection p) {

        return new SettlementPreviewDto.PermanentAdvance(

                p.getAdvanceId(),

                p.getRequestCode(),

                p.getReceivingDate(),

                p.getOriginalAmount(),

                p.getRecoveredAmount(),

                p.getOutstandingAmount(),

                p.getMonthlyDeduction(),

                p.getBonusOffset(),

                p.getAnnualLeaveOffset()
        );
    }
}
