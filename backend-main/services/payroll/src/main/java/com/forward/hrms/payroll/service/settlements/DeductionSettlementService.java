package com.forward.hrms.payroll.service.settlements;

import com.forward.hrms.payroll.entity.EmployeeFinalSettlement;
import com.forward.hrms.payroll.entity.EmployeeFinalSettlementDeduction;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class DeductionSettlementService {

    @Transactional
    public BigDecimal process(EmployeeFinalSettlement settlement) {

        if (settlement.getDeductions() == null || settlement.getDeductions().isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal totalRecovered = BigDecimal.ZERO;

        for (EmployeeFinalSettlementDeduction deduction : settlement.getDeductions()) {

            if (deduction.getAmount() == null || deduction.getAmount().signum() <= 0) {
                continue;
            }

            //deduction.setRecoveredAt(LocalDateTime.now());

            totalRecovered = totalRecovered.add(deduction.getAmount());
        }

        return totalRecovered;
    }

}
