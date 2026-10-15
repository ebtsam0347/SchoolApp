package com.forward.hrms.payroll.settlement.calculator;

import com.forward.hrms.payroll.entity.EmployeeSalary;
import com.forward.hrms.payroll.enums.ForcedTerminationDeductionPercentage;
import com.forward.hrms.payroll.enums.NoticePayPercentage;
import com.forward.hrms.payroll.enums.SettlementAdjustmentType;
import com.forward.hrms.payroll.settlement.context.SettlementCalculationContext;
import com.forward.hrms.payroll.settlement.context.SettlementCalculationResult;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plain unit test (no Spring context needed — the calculator is pure
 * computation) proving notice pay / forced deduction round CEILING at
 * scale 2 via the project's {@code Money} helper, matching every other
 * money computation in the settlement. Before this fix both used
 * {@code RoundingMode.HALF_UP} directly, disagreeing with the rest of
 * the settlement — see SettlementLeaveEncashmentCalculator's own
 * comment about the same HALF_UP-vs-CEILING mistake, fixed there
 * earlier but missed here.
 */
class SettlementTerminationPolicyCalculatorTest {

    private final SettlementTerminationPolicyCalculator calculator =
            new SettlementTerminationPolicyCalculator();

    // 33333.33 * 25% = 8333.3325 -> HALF_UP would give 8333.33,
    // CEILING must round up to 8333.34.
    @Test
    void noticePay_roundsCeilingNotHalfUp() {
        SettlementCalculationContext ctx = context(new BigDecimal("33333.33"));
        ctx.setAdjustmentType(SettlementAdjustmentType.NOTICE_PAY);
        ctx.setNoticePayPercentage(NoticePayPercentage.PERCENT_25);

        SettlementCalculationResult result = new SettlementCalculationResult();
        calculator.calculate(ctx, result);

        assertThat(result.getNoticePayAmount()).isEqualByComparingTo("8333.34");
    }

    // 10000.01 * 50% = 5000.005 -> HALF_UP would give 5000.01 too (rounds
    // up on the nickel), so use a case where HALF_UP and CEILING clearly
    // diverge: 10000.03 * 50% = 5000.015 -> HALF_UP = 5000.02 (rounds to
    // even/nearest), CEILING = 5000.02 as well at this exact half-point in
    // some JDKs; use a non-half fractional case instead for an
    // unambiguous divergence.
    @Test
    void forcedDeduction_roundsCeilingNotHalfUp() {
        SettlementCalculationContext ctx = context(new BigDecimal("33333.33"));
        ctx.setAdjustmentType(SettlementAdjustmentType.FORCED_DEDUCTION);
        ctx.setForcedDeductionPercentage(ForcedTerminationDeductionPercentage.PERCENT_25);

        SettlementCalculationResult result = new SettlementCalculationResult();
        calculator.calculate(ctx, result);

        // 33333.33 * 25 / 100 = 8333.3325 -> CEILING scale 2 = 8333.34.
        // The old RoundingMode.HALF_UP implementation produced 8333.33.
        assertThat(result.getForcedDeductionAmount()).isEqualByComparingTo("8333.34");
    }

    @Test
    void none_leavesBothAmountsZero() {
        SettlementCalculationContext ctx = context(new BigDecimal("50000.00"));
        ctx.setAdjustmentType(SettlementAdjustmentType.NONE);

        SettlementCalculationResult result = new SettlementCalculationResult();
        calculator.calculate(ctx, result);

        assertThat(result.getNoticePayAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getForcedDeductionAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    private SettlementCalculationContext context(BigDecimal basic) {
        EmployeeSalary salary = new EmployeeSalary();
        salary.setBasic(basic);
        SettlementCalculationContext ctx = new SettlementCalculationContext();
        ctx.setSalary(salary);
        return ctx;
    }
}
