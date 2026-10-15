package com.forward.hrms.payroll.settlement.calculator;

import com.forward.hrms.commons.core.money.Money;
import com.forward.hrms.employee.enums.GradeType;
import com.forward.hrms.payroll.client.AttendanceClient;
import com.forward.hrms.payroll.entity.PayrollConfig;
import com.forward.hrms.payroll.settlement.context.SettlementCalculationContext;
import com.forward.hrms.payroll.settlement.context.SettlementCalculationResult;
import com.forward.hrms.payroll.settlement.context.SettlementCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Grade-conditional canteen deduction for the final-settlement period
 * (1st of the termination month through the last working day). Mirrors
 * {@code PayrollComputeService.computeCanteen} for the regular monthly
 * run — management pays a flat weekly subscription, supervisor/labour
 * pay per meal — so canteen usage in a terminating employee's partial
 * month isn't silently dropped just because there's no regular payslip
 * for that period.
 */
@Service
@RequiredArgsConstructor
public class SettlementCanteenCalculator implements SettlementCalculator {

    private final AttendanceClient attendanceClient;

    @Override
    public void calculate(
            SettlementCalculationContext ctx,
            SettlementCalculationResult result) {

        PayrollConfig stat = ctx.getPayrollConfig();

        if (stat == null) {
            result.setCanteenAmount(BigDecimal.ZERO);
            return;
        }

        GradeType gradeType = ctx.getEmployeeGradeType() == null
                ? GradeType.LABOUR
                : ctx.getEmployeeGradeType();

        BigDecimal rate = gradeType == GradeType.MANAGEMENT
                ? stat.getCanteenPerDayRateManagement()
                : stat.getCanteenPerDayRateOther();

        if (rate == null || rate.signum() <= 0) {
            result.setCanteenAmount(BigDecimal.ZERO);
            return;
        }

        LocalDate lastWorkingDay = ctx.getLastWorkingDay();
        LocalDate periodStart = lastWorkingDay.withDayOfMonth(1);

        AttendanceClient.CanteenUsage usage = attendanceClient.canteenUsage(
                ctx.getEmployeeId(),
                periodStart,
                lastWorkingDay,
                null);

        if (usage.days() == 0) {
            result.setCanteenAmount(BigDecimal.ZERO);
            return;
        }

        BigDecimal amount = gradeType == GradeType.MANAGEMENT
                ? Money.of(rate).multiply(BigDecimal.valueOf(7L * usage.weeks())).amount()
                : Money.of(rate).multiply(BigDecimal.valueOf(usage.days())).amount();

        result.setCanteenAmount(amount);
    }
}
