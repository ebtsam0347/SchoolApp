package com.forward.hrms.payroll.settlement.engine;

import com.forward.hrms.payroll.settlement.context.SettlementCalculationContext;
import com.forward.hrms.payroll.settlement.context.SettlementCalculationResult;
import com.forward.hrms.payroll.settlement.context.SettlementCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SettlementCalculationEngine {

    private final List<SettlementCalculator> calculators;

    public SettlementCalculationResult calculate(
            SettlementCalculationContext context) {

        SettlementCalculationResult result = new SettlementCalculationResult();

        calculators.forEach(c -> c.calculate(context, result));

        BigDecimal grossSettlement = result.getGrossSalary()
                .add(result.getGratuity())
                .add(result.getLeaveEncashment())
                .add(result.getOutstandingBonus())
                .add(result.getNoticePayAmount());

        BigDecimal totalDeductions = result.getIncomeTax()
                .add(result.getOtherDeductions())
                .add(result.getPermanentAdvanceTotal())
                .add(result.getShortTermAdvance())
                .add(result.getRfdAdvanceAmount())
                .add(result.getLeaveRecovery())
                .add(result.getFairPriceShopAmount())
                .add(result.getCanteenAmount())
                .add(result.getForcedDeductionAmount());

        result.setGrossSettlement(grossSettlement);
        result.setTotalDeductions(totalDeductions);
        result.setNetSettlement(grossSettlement.subtract(totalDeductions));

        return result;
    }
}