package com.forward.hrms.payroll.service.settlements;

import com.forward.hrms.payroll.entity.EmployeeFinalSettlement;
import com.forward.hrms.payroll.entity.EmployeeFinalSettlementDeduction;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FairPriceShopSettlementService {

    @Transactional
    public BigDecimal process(EmployeeFinalSettlement settlement) {

        BigDecimal amount = settlement.getFairPriceShopAmount();

        if (amount == null || amount.signum() <= 0) {
            return BigDecimal.ZERO;
        }

        /*
         * Search existing deduction snapshot.
         */

        Optional<EmployeeFinalSettlementDeduction> existing =
                settlement.getDeductions()
                        .stream()
                        .filter(d ->
                                "FAIR_PRICE_SHOP"
                                        .equals(d.getDeductionCode()))
                        .findFirst();

        EmployeeFinalSettlementDeduction deduction;

        if (existing.isPresent()) {

            deduction = existing.get();

        } else {

            deduction = new EmployeeFinalSettlementDeduction();

            deduction.setDeductionCode("FAIR_PRICE_SHOP");

            deduction.setDeductionName("Fair Price Shop");

            deduction.setAmount(amount);

            settlement.addDeduction(deduction);
        }

        //deduction.setRecoveredAt(LocalDateTime.now());

        return amount;
    }

}
