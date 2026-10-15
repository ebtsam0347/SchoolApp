package com.forward.hrms.payroll.service.settlements;

import com.forward.hrms.payroll.entity.EmployeeFinalSettlement;
import com.forward.hrms.payroll.entity.EmployeeFinalSettlementAdvanceDetail;
import com.forward.hrms.payroll.entity.PermanentAdvance;
import com.forward.hrms.payroll.entity.PermanentAdvanceDeduction;
import com.forward.hrms.payroll.enums.AdvanceStatus;
import com.forward.hrms.payroll.repository.PermanentAdvanceDeductionRepository;
import com.forward.hrms.payroll.repository.PermanentAdvanceRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PermanentAdvanceSettlementService {

    private final PermanentAdvanceRepository advanceRepository;
    private final PermanentAdvanceDeductionRepository deductionRepository;

    @Transactional
    public void recover(
            EmployeeFinalSettlement settlement,
            String recoveredBy) {

        if (settlement.getAdvanceDetails() == null ||
                settlement.getAdvanceDetails().isEmpty()) {
            return;
        }

        for (EmployeeFinalSettlementAdvanceDetail detail
                : settlement.getAdvanceDetails()) {

            PermanentAdvance advance =
                    advanceRepository.findById(detail.getAdvanceId())
                            .orElseThrow(() ->
                                    new IllegalArgumentException(
                                            "Permanent advance not found : "
                                                    + detail.getAdvanceId()));

            BigDecimal outstanding = detail.getOutstandingAmount();

            if (outstanding == null ||
                    outstanding.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            PermanentAdvanceDeduction deduction =
                    buildDeduction(
                            settlement,
                            advance,
                            outstanding);

            deductionRepository.save(deduction);

            /*
             * Snapshot update
             */

            detail.setSettlementRecovered(outstanding);

            detail.setRecoveredAt(LocalDateTime.now());

            detail.setRecoveredBy(recoveredBy);

            detail.setAdvanceStatus(AdvanceStatus.APPROVED);

            /*
             * Original advance update
             */

            // Status stays APPROVED; closed_at marks it recovered.
            advance.setClosedAt(LocalDateTime.now());

            advanceRepository.save(advance);
        }
    }

    private PermanentAdvanceDeduction buildDeduction(
            EmployeeFinalSettlement settlement,
            PermanentAdvance advance,
            BigDecimal amount) {

        PermanentAdvanceDeduction deduction = new PermanentAdvanceDeduction();

        deduction.setEmployeeId(advance.getEmployeeId());

        deduction.setSource(com.forward.hrms.payroll.enums.PermanentAdvanceDeductionSource.SETTLEMENT);

        deduction.setSourceRefId(settlement.getId());

        deduction.setDeductedAmount(amount);

        deduction.setRemainingBalance(BigDecimal.ZERO);

        deduction.setTransactionDate(settlement.getLastWorkingDay());

        deduction.setDescription("closing entry");

        return deduction;
    }

}