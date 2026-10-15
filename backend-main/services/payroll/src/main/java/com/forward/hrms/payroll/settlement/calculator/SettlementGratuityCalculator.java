package com.forward.hrms.payroll.settlement.calculator;

import com.forward.hrms.payroll.dto.response.GratuityDto;
import com.forward.hrms.payroll.service.GratuityService;
import com.forward.hrms.payroll.settlement.context.SettlementCalculationContext;
import com.forward.hrms.payroll.settlement.context.SettlementCalculationResult;
import com.forward.hrms.payroll.settlement.context.SettlementCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SettlementGratuityCalculator implements SettlementCalculator {

    private final GratuityService gratuityService;

    @Override
    public void calculate(
            SettlementCalculationContext ctx,
            SettlementCalculationResult result) {

        GratuityDto g = gratuityService.compute(
                        ctx.getEmployeeId(),
                        ctx.getLastWorkingDay());

        result.setGratuity(g.gratuity());
        result.setGratuityEligible(g.eligible());
        result.setGratuityYears(g.completedYears());
    }
}
