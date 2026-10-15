package com.forward.hrms.payroll.settlement.calculator;

import com.forward.hrms.commons.core.money.Money;
import com.forward.hrms.payroll.client.AttendanceClient;
import com.forward.hrms.payroll.dto.response.CombinedLeaveSummaryDto;
import com.forward.hrms.payroll.dto.response.TerminationLeaveSummaryDto;
import com.forward.hrms.payroll.dto.response.TerminationLeavesSummaryDto;
import com.forward.hrms.payroll.settlement.context.SettlementCalculationContext;
import com.forward.hrms.payroll.settlement.context.SettlementCalculationResult;
import com.forward.hrms.payroll.settlement.context.SettlementCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Values the leave-cycle day counts attendance-service returns
 * (annual leave kept separate, medical/M1 + casual combined) into
 * money. Attendance owns leave-cycle calculations; this calculator
 * only converts payable/recoverable days into an encashment payout
 * or an "excessive leave" recovery deduction.
 */
@Service
@RequiredArgsConstructor
public class SettlementLeaveEncashmentCalculator
        implements SettlementCalculator {

    private static final BigDecimal DAY_DIVISOR = BigDecimal.valueOf(26);

    private final AttendanceClient attendanceClient;

    @Override
    public void calculate(
            SettlementCalculationContext context,
            SettlementCalculationResult result) {

        TerminationLeavesSummaryDto response = attendanceClient.terminationLeavesSummary(
                context.getEmployeeId(),
                context.getLastWorkingDay());

        if (response == null) {
            return;
        }

        BigDecimal basicSalary = BigDecimal.ZERO;

        if (context.getSalary() != null && context.getSalary().getBasic() != null) {

            basicSalary = context.getSalary().getBasic();
        }

        // Company-wide payroll rounding rule (CEILING, scale 2) lives in
        // the Money helper — this used to round HALF_UP locally, which
        // disagreed with every other money computation in the codebase.
        BigDecimal dailyBasic = Money.of(basicSalary).divide(DAY_DIVISOR).amount();

        TerminationLeaveSummaryDto annualLeave =
                valueAnnualLeave(response.annualLeave(), dailyBasic);

        CombinedLeaveSummaryDto medicalAndCasualLeave =
                valueMedicalAndCasualLeave(response.medicalAndCasualLeave(), dailyBasic);

        BigDecimal annualExcessiveDays = annualLeave != null ? nvl(annualLeave.recoverableDays()) : BigDecimal.ZERO;
        BigDecimal annualExcessiveAmount = annualLeave != null ? nvl(annualLeave.recoveryAmount()) : BigDecimal.ZERO;

        BigDecimal medicalCasualExcessiveDays = medicalAndCasualLeave != null ? nvl(medicalAndCasualLeave.recoverableDays()) : BigDecimal.ZERO;
        BigDecimal medicalCasualExcessiveAmount = medicalAndCasualLeave != null ? nvl(medicalAndCasualLeave.recoveryAmount()) : BigDecimal.ZERO;

        // Annual is the only encashable type — total encashment is its payout alone.
        BigDecimal totalEncashment = annualLeave != null ? nvl(annualLeave.encashmentAmount()) : BigDecimal.ZERO;
        BigDecimal totalRecovery = annualExcessiveAmount.add(medicalCasualExcessiveAmount);

        result.setLeaveEncashment(totalEncashment);
        result.setLeaveRecovery(totalRecovery);

        result.setAnnualExcessiveDays(annualExcessiveDays);
        result.setAnnualExcessiveAmount(annualExcessiveAmount);
        result.setMedicalCasualExcessiveDays(medicalCasualExcessiveDays);
        result.setMedicalCasualExcessiveAmount(medicalCasualExcessiveAmount);

        result.setLeavesSummaries(
                new TerminationLeavesSummaryDto(
                        annualLeave,
                        medicalAndCasualLeave,
                        totalEncashment,
                        totalRecovery
                )
        );
    }

    /**
     * Annual leave is the only encashable type — payableDays are
     * paid out at dailyBasic, and any days used beyond what was
     * earned (recoverableDays) are recovered at the same rate.
     */
    private TerminationLeaveSummaryDto valueAnnualLeave(
            TerminationLeaveSummaryDto leave,
            BigDecimal dailyBasic) {

        if (leave == null) {
            return null;
        }

        BigDecimal encashmentAmount = calculateAmount(
                leave.encashable() ? leave.payableDays() : null,
                dailyBasic);

        BigDecimal recoveryAmount = calculateAmount(
                leave.recoverableDays(),
                dailyBasic);

        return new TerminationLeaveSummaryDto(

                leave.leaveTypeId(),
                leave.leaveCode(),
                leave.leaveName(),
                leave.encashable(),

                leave.cycleStart(),
                leave.cycleEnd(),

                leave.allocated(),
                leave.earned(),
                leave.used(),
                leave.pending(),
                leave.payableDays(),
                leave.recoverableDays(),

                encashmentAmount,
                recoveryAmount
        );
    }

    /**
     * Medical (M1) + Casual are never individually encashable —
     * encashmentAmount is always zero here; only the excess used
     * beyond earned (recoverableDays) is recovered.
     */
    private CombinedLeaveSummaryDto valueMedicalAndCasualLeave(
            CombinedLeaveSummaryDto combined,
            BigDecimal dailyBasic) {

        if (combined == null) {
            return null;
        }

        BigDecimal recoveryAmount = calculateAmount(
                combined.recoverableDays(),
                dailyBasic);

        return new CombinedLeaveSummaryDto(

                combined.allocated(),
                combined.earned(),
                combined.used(),
                combined.pending(),
                combined.payableDays(),
                combined.recoverableDays(),

                BigDecimal.ZERO,
                recoveryAmount
        );
    }

    private BigDecimal calculateAmount(
            BigDecimal days,
            BigDecimal dailyBasic) {

        if (days == null || days.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        return Money.of(dailyBasic).multiply(days).amount();
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}