package com.forward.hrms.payroll.service.settlements;

import com.forward.hrms.payroll.entity.EmployeeFinalSettlement;
import com.forward.hrms.payroll.entity.ShortTermAdvance;
import com.forward.hrms.payroll.enums.ShortTermAdvanceStatus;
import com.forward.hrms.payroll.repository.ShortTermAdvanceRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ShortTermAdvanceSettlementService {

    private final ShortTermAdvanceRepository repository;

    @Transactional
    public BigDecimal process(EmployeeFinalSettlement settlement) {

        List<ShortTermAdvance> advances = repository.findByEmployeeIdAndTargetYearAndTargetMonthAndStatusAndFinalSettlementIdIsNull(
                settlement.getEmployeeId(),
                settlement.getLastWorkingDay().getYear(),
                settlement.getLastWorkingDay().getMonthValue(),
                ShortTermAdvanceStatus.APPROVED
        );

        if (advances.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal recovered = BigDecimal.ZERO;

        for (ShortTermAdvance advance : advances) {

            // Status stays APPROVED; final_settlement_id marks it recovered.
            advance.setFinalSettlementId(settlement.getId());

            recovered = recovered.add(advance.getAmount());
        }

        return recovered;
    }

    /**
     * Undo {@link #process}. Unlinks exactly the short-term advances this
     * settlement recovered.
     */
    @Transactional
    public void reverse(EmployeeFinalSettlement settlement) {

        List<ShortTermAdvance> advances = repository.findAllByFinalSettlementId(settlement.getId());

        for (ShortTermAdvance advance : advances) {

            advance.setFinalSettlementId(null);
        }
    }
}