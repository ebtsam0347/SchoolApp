package com.forward.hrms.payroll.service.settlements;

import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.UnauthorizedError;
import com.forward.hrms.payroll.entity.EmployeeFinalSettlement;
import com.forward.hrms.payroll.entity.EmployeeFinalSettlementAdvanceDetail;
import com.forward.hrms.payroll.entity.PermanentAdvance;
import com.forward.hrms.payroll.entity.PermanentAdvanceDeduction;
import com.forward.hrms.payroll.enums.PermanentAdvanceDeductionSource;
import com.forward.hrms.payroll.repository.PermanentAdvanceDeductionRepository;
import com.forward.hrms.payroll.repository.PermanentAdvanceRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PermanentAdvanceSettlementServiceNew {

    private final PermanentAdvanceRepository advanceRepository;

    private final PermanentAdvanceDeductionRepository deductionRepository;

    /**
     * Recover the employee's whole permanent-advance balance — the
     * settlement's stored {@code permanentAdvance} total (APPROVED advances −
     * all deductions, from the preview) — as one "closing entry" row
     * (SETTLEMENT, settlement id), and mark every open advance in the
     * breakdown settled ({@code closed_at}). The row is written even when
     * there is no open advance left, so an inconsistent balance is still
     * recovered rather than lost.
     */
    @Transactional
    public BigDecimal process(EmployeeFinalSettlement settlement) {

        CurrentUser user = currentUser();

        BigDecimal total = settlement.getPermanentAdvance() == null
                ? BigDecimal.ZERO : settlement.getPermanentAdvance();

        if (settlement.getAdvanceDetails() != null) {

            for (EmployeeFinalSettlementAdvanceDetail detail : settlement.getAdvanceDetails()) {

                PermanentAdvance advance = advanceRepository.findById(detail.getAdvanceId())
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Permanent Advance not found : "
                                                + detail.getAdvanceId()));

                // Status stays APPROVED; closed_at marks it settled and drops
                // it from every open-advance query.
                advance.setClosedAt(LocalDateTime.now());

                advanceRepository.save(advance);

                BigDecimal recovery = detail.getOutstandingAmount() == null
                        ? BigDecimal.ZERO : detail.getOutstandingAmount().max(BigDecimal.ZERO);

                detail.setSettlementRecovered(recovery);

                detail.setRecoveredAmount(detail.getRecoveredAmount().add(recovery));

                detail.setOutstandingAmount(BigDecimal.ZERO);

                detail.setRecoveredAt(LocalDateTime.now());

                detail.setRecoveredBy(String.valueOf(user.userId()));
            }
        }

        if (total.signum() > 0) {

            PermanentAdvanceDeduction deduction = new PermanentAdvanceDeduction();

            deduction.setEmployeeId(settlement.getEmployeeId());

            deduction.setSource(PermanentAdvanceDeductionSource.SETTLEMENT);

            deduction.setSourceRefId(settlement.getId());

            deduction.setDeductedAmount(total);

            deduction.setRemainingBalance(BigDecimal.ZERO);

            deduction.setTransactionDate(settlement.getLastWorkingDay());

            deduction.setDescription("closing entry");

            deductionRepository.save(deduction);
        }

        return total;
    }

    /**
     * Undo {@link #process}: deletes the settlement's closing entry, reopens
     * every advance it settled, and restores each detail's
     * outstanding/recovered snapshot.
     */
    @Transactional
    public void reverse(EmployeeFinalSettlement settlement) {

        // Idempotent if reverse() is ever called twice.
        deductionRepository.deleteBySource(PermanentAdvanceDeductionSource.SETTLEMENT, settlement.getId());

        if (settlement.getAdvanceDetails() == null) {

            return;
        }

        for (EmployeeFinalSettlementAdvanceDetail detail : settlement.getAdvanceDetails()) {

            if (detail.getRecoveredAt() == null) {

                continue;
            }

            PermanentAdvance advance = advanceRepository.findById(detail.getAdvanceId())
                    .orElseThrow(() ->
                            new IllegalArgumentException(
                                    "Permanent Advance not found : "
                                            + detail.getAdvanceId()));

            advance.setClosedAt(null);

            advanceRepository.save(advance);

            BigDecimal recovery = detail.getSettlementRecovered() == null
                    ? BigDecimal.ZERO : detail.getSettlementRecovered();

            detail.setRecoveredAmount(detail.getRecoveredAmount().subtract(recovery));

            detail.setOutstandingAmount(recovery);

            detail.setSettlementRecovered(BigDecimal.ZERO);

            detail.setBonusRecovered(BigDecimal.ZERO);

            detail.setLeaveRecovered(BigDecimal.ZERO);

            detail.setRecoveredAt(null);

            detail.setRecoveredBy(null);
        }
    }

    private CurrentUser currentUser() {
        return CurrentUserHolder.get()
                .orElseThrow(() -> new UnauthorizedError("no current user"));
    }

}
