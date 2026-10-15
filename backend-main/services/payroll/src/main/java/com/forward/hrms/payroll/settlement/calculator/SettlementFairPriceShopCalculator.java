package com.forward.hrms.payroll.settlement.calculator;

import com.forward.hrms.payroll.client.FairPriceShopClient;
import com.forward.hrms.payroll.settlement.context.SettlementCalculationContext;
import com.forward.hrms.payroll.settlement.context.SettlementCalculationResult;
import com.forward.hrms.payroll.settlement.context.SettlementCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class SettlementFairPriceShopCalculator implements SettlementCalculator {

    private final FairPriceShopClient fairPriceShopClient;

    @Override
    public void calculate(
            SettlementCalculationContext context,
            SettlementCalculationResult result) {

        if (context.getEmployeeCode() == null
                || context.getEmployeeCode().isBlank()) {

            result.setFairPriceShopAmount(BigDecimal.ZERO);
            return;
        }

        BigDecimal amount = fairPriceShopClient.outstandingAmount(
                context.getEmployeeCode(),
                context.getLastWorkingDay());

        result.setFairPriceShopAmount(amount == null ? BigDecimal.ZERO : amount);
    }
}
