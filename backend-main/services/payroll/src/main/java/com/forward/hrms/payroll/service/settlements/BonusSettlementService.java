package com.forward.hrms.payroll.service.settlements;

import com.forward.hrms.payroll.entity.BonusEventAmount;
import com.forward.hrms.payroll.entity.EmployeeFinalSettlement;
import com.forward.hrms.payroll.entity.EmployeeFinalSettlementBonusDetail;
import com.forward.hrms.payroll.enums.BonusPaymentStatus;
import com.forward.hrms.payroll.repository.BonusEventAmountRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class BonusSettlementService {

    private final BonusEventAmountRepository repository;

    @Transactional
    public BigDecimal process(EmployeeFinalSettlement settlement) {

        if (settlement.getBonuses() == null || settlement.getBonuses().isEmpty()) {

            return BigDecimal.ZERO;
        }

        BigDecimal totalPaid = BigDecimal.ZERO;

        for (EmployeeFinalSettlementBonusDetail detail : settlement.getBonuses()) {

            BonusEventAmount amount = repository.findByBonusEventIdAndEmployeeId(
                            detail.getBonusEventId(),
                            settlement.getEmployeeId()
                    )
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Bonus Event Amount not found : "
                                    + detail.getBonusEventId()));

            BigDecimal alreadyPaid = amount.getPaidAmount() == null
                    ? BigDecimal.ZERO
                    : amount.getPaidAmount();

            BigDecimal payment = detail.getOutstandingAmount();

            BigDecimal total = alreadyPaid.add(payment);

            amount.setPaidAmount(total);

            amount.setPaidAt(LocalDateTime.now());

            if (total.compareTo(amount.getFullBonus()) >= 0) {

                amount.setPaymentStatus(BonusPaymentStatus.PAID);

            } else {

                amount.setPaymentStatus(BonusPaymentStatus.PARTIALLY_PAID);
            }

            repository.save(amount);

            detail.setPaidAmount(payment);

            detail.setOutstandingAmount(BigDecimal.ZERO);

            totalPaid = totalPaid.add(payment);
        }

        return totalPaid;
    }

    /**
     * Undo {@link #process}. For each bonus detail this settlement paid,
     * subtracts exactly what it paid back out of the {@link BonusEventAmount}
     * ledger, recomputes the payment status, and restores the detail row
     * to its pre-settlement (unpaid) snapshot.
     */
    @Transactional
    public void reverse(EmployeeFinalSettlement settlement) {

        if (settlement.getBonuses() == null || settlement.getBonuses().isEmpty()) {
            return;
        }

        for (EmployeeFinalSettlementBonusDetail detail : settlement.getBonuses()) {

            BigDecimal paidBySettlement = detail.getPaidAmount();

            if (paidBySettlement == null || paidBySettlement.signum() <= 0) {
                continue;
            }

            BonusEventAmount amount = repository.findByBonusEventIdAndEmployeeId(
                            detail.getBonusEventId(),
                            settlement.getEmployeeId()
                    )
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Bonus Event Amount not found : "
                                    + detail.getBonusEventId()));

            BigDecimal alreadyPaid = amount.getPaidAmount() == null
                    ? BigDecimal.ZERO
                    : amount.getPaidAmount();

            BigDecimal restored = alreadyPaid.subtract(paidBySettlement);

            if (restored.signum() < 0) {
                restored = BigDecimal.ZERO;
            }

            amount.setPaidAmount(restored);

            if (restored.signum() <= 0) {

                amount.setPaymentStatus(BonusPaymentStatus.PENDING);

            } else if (restored.compareTo(amount.getFullBonus()) >= 0) {

                amount.setPaymentStatus(BonusPaymentStatus.PAID);

            } else {

                amount.setPaymentStatus(BonusPaymentStatus.PARTIALLY_PAID);
            }

            repository.save(amount);

            detail.setOutstandingAmount(paidBySettlement);

            detail.setPaidAmount(BigDecimal.ZERO);
        }
    }

}
