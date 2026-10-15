package com.forward.hrms.payroll.settlement.context;

public interface SettlementCalculator {

    void calculate(
            SettlementCalculationContext context,
            SettlementCalculationResult result
    );
}
