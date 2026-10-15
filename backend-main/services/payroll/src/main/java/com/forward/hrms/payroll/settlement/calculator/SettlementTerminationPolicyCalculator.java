package com.forward.hrms.payroll.settlement.calculator;

import com.forward.hrms.commons.core.money.Money;
import com.forward.hrms.payroll.enums.ForcedTerminationDeductionPercentage;
import com.forward.hrms.payroll.enums.NoticePayPercentage;
import com.forward.hrms.payroll.enums.SettlementAdjustmentType;
import com.forward.hrms.payroll.settlement.context.SettlementCalculationContext;
import com.forward.hrms.payroll.settlement.context.SettlementCalculationResult;
import com.forward.hrms.payroll.settlement.context.SettlementCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class SettlementTerminationPolicyCalculator implements SettlementCalculator {

    @Override
    public void calculate(
            SettlementCalculationContext context,
            SettlementCalculationResult result) {

        SettlementAdjustmentType adjustmentType = context.getAdjustmentType() == null
                ? SettlementAdjustmentType.NONE
                : context.getAdjustmentType();

        result.setAdjustmentType(adjustmentType);

        BigDecimal basic = context.getSalary().getBasic();

        // Purpose of switch case to ensure only one adjustment is applied as user selected.
        switch (adjustmentType) {

            case NOTICE_PAY -> {

                calculateNoticePay(basic, context, result);

                result.setForcedDeductionPercentage(ForcedTerminationDeductionPercentage.PERCENT_0);
                result.setForcedDeductionAmount(BigDecimal.ZERO);
            }

            case FORCED_DEDUCTION -> {

                calculateForcedDeduction(basic, context, result);

                result.setNoticePayPercentage(NoticePayPercentage.NONE);
                result.setNoticePayAmount(BigDecimal.ZERO);
            }

            case NONE -> {

                result.setNoticePayPercentage(NoticePayPercentage.NONE);
                result.setNoticePayAmount(BigDecimal.ZERO);

                result.setForcedDeductionPercentage(ForcedTerminationDeductionPercentage.PERCENT_0);
                result.setForcedDeductionAmount(BigDecimal.ZERO);
            }
        }
    }

    private void calculateNoticePay(
            BigDecimal basic,
            SettlementCalculationContext context,
            SettlementCalculationResult result) {

        NoticePayPercentage percentage = context.getNoticePayPercentage();

        if (percentage == null) {
            result.setNoticePayAmount(BigDecimal.ZERO);
            return;
        }

        // Company-wide payroll rounding rule (CEILING, scale 2) lives in
        // the Money helper — this used to round HALF_UP locally, which
        // disagreed with every other money computation in this settlement.
        BigDecimal amount = Money.of(basic)
                .proration(percentage.percentage(), BigDecimal.valueOf(100))
                .amount();

        result.setNoticePayPercentage(percentage);
        result.setNoticePayAmount(amount);
    }


    private void calculateForcedDeduction(
            BigDecimal basic,
            SettlementCalculationContext context,
            SettlementCalculationResult result) {

        ForcedTerminationDeductionPercentage percentage = context.getForcedDeductionPercentage();

        if (percentage == null) {
            result.setForcedDeductionAmount(BigDecimal.ZERO);
            return;
        }

        // Company-wide payroll rounding rule (CEILING, scale 2) lives in
        // the Money helper — this used to round HALF_UP locally, which
        // disagreed with every other money computation in this settlement.
        BigDecimal amount = Money.of(basic)
                .proration(percentage.percentage(), BigDecimal.valueOf(100))
                .amount();

        result.setForcedDeductionPercentage(percentage);
        result.setForcedDeductionAmount(amount);
    }
}
