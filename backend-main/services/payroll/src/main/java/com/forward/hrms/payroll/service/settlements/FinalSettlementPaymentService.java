package com.forward.hrms.payroll.service.settlements;

import com.forward.hrms.payroll.dto.request.SettlementPaymentRequest;
import com.forward.hrms.payroll.dto.response.PaymentResultDto;
import com.forward.hrms.payroll.entity.EmployeeFinalSettlement;
import com.forward.hrms.payroll.entity.OffCyclePayment;
import com.forward.hrms.payroll.enums.OffCyclePaymentMode;
import com.forward.hrms.payroll.enums.OffCyclePaymentSource;
import com.forward.hrms.payroll.enums.OffCyclePaymentStatus;
import com.forward.hrms.payroll.repository.OffCyclePaymentRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class FinalSettlementPaymentService {

    private final OffCyclePaymentRepository offCyclePaymentRepository;

    private final LeaveSettlementService leaveSettlementService;

    private final BonusSettlementService bonusSettlementService;

    private final PermanentAdvanceSettlementServiceNew permanentAdvanceSettlementServiceNew;

    private final ShortTermAdvanceSettlementService shortTermAdvanceSettlementService;

    private final RfdAdvanceSettlementService rfdAdvanceSettlementService;

    private final FairPriceShopSettlementService fairPriceShopSettlementService;

    private final DeductionSettlementService deductionSettlementService;

    @Transactional
    public PaymentResultDto process(
            EmployeeFinalSettlement settlement
    ) {

        /*
         * -------------------------------------------------------
         * 1. Create Payment Voucher
         * -------------------------------------------------------
         */
        // no need of this because it is not relevant for this process
        //createOffCyclePayment(settlement, request);

        /*
         * -------------------------------------------------------
         * 2. Leave Encashment
         * -------------------------------------------------------
         */
        BigDecimal leavePaid = leaveSettlementService.process(settlement);

        /*
         * -------------------------------------------------------
         * 3. Bonus Settlement
         * -------------------------------------------------------
         */
        BigDecimal bonusPaid = bonusSettlementService.process(settlement);

        /*
         * -------------------------------------------------------
         * 4. Permanent Advance Recovery
         * -------------------------------------------------------
         */
        BigDecimal permanentAdvanceRecovered = permanentAdvanceSettlementServiceNew.process(settlement);

        /*
         * -------------------------------------------------------
         * 5. Short Term Advance Recovery
         * -------------------------------------------------------
         */
        BigDecimal shortTermRecovered = shortTermAdvanceSettlementService.process(settlement);

        /*
         * -------------------------------------------------------
         * 6. RFD Advance Recovery
         * -------------------------------------------------------
         */
        BigDecimal rfdRecovered = rfdAdvanceSettlementService.process(settlement);

        /*
         * -------------------------------------------------------
         * 7. Fair Price Shop
         * -------------------------------------------------------
         */
        BigDecimal fairPriceRecovered = fairPriceShopSettlementService.process(settlement);

        /*
         * -------------------------------------------------------
         * 8. Outstanding Deductions
         * -------------------------------------------------------
         */
        BigDecimal deductionsRecovered = deductionSettlementService.process(settlement);

        return PaymentResultDto.builder()

                .leavePaid(leavePaid)

                .bonusPaid(bonusPaid)

                .advanceRecovered(permanentAdvanceRecovered.add(shortTermRecovered).add(rfdRecovered))

                .fairPriceRecovered(fairPriceRecovered)

                .deductionsRecovered(deductionsRecovered)

                .netPaid(settlement.getNetSettlement())

                .build();
    }

    /**
     * Undo {@link #process}. HR policy: closing (leave/bonus/advances/
     * deductions) happens at settlement CREATION, so rejecting a DRAFT
     * settlement must reverse exactly that work — otherwise the employee
     * stays "closed" (advances recovered, leave encashed, bonus paid)
     * against a settlement that no longer exists. Order is the reverse
     * of {@link #process}; fair-price-shop and outstanding-deduction
     * snapshots don't touch any other table, so there's nothing to
     * reverse for them.
     */
    @Transactional
    public void reverse(EmployeeFinalSettlement settlement) {

        shortTermAdvanceSettlementService.reverse(settlement);

        permanentAdvanceSettlementServiceNew.reverse(settlement);

        bonusSettlementService.reverse(settlement);

        leaveSettlementService.reverse(settlement);
    }

    private void createOffCyclePayment(
            EmployeeFinalSettlement settlement,
            SettlementPaymentRequest request
    ) {

        BigDecimal amount = settlement.getNetSettlement();

        if (amount == null) {
            throw new IllegalStateException(
                    "Settlement net amount cannot be null."
            );
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException(
                    "Off-cycle payment can only be created for a positive settlement amount."
            );
        }

        OffCyclePayment payment = new OffCyclePayment();

        payment.setEmployeeId(settlement.getEmployeeId());

        payment.setPaidOn(request.paymentDate());

        payment.setAmount(amount);

        payment.setSource(OffCyclePaymentSource.FINAL_SETTLEMENT);

        // System-generated, so no HR master type: the settlement engine
        // writes this voucher, nobody picks it from a dropdown.
        payment.setOffCyclePaymentTypeId(null);

        payment.setReason("Final Settlement #" + settlement.getId());

        payment.setPaymentMode(OffCyclePaymentMode.CASH);

        payment.setReferenceNo(trimToNull(request.referenceNo()));

        // it is wrong because we are setting the source event ID to the settlement ID.
        //payment.setSourceEventId(settlement.getId()); // as this belongs to payroll.bonus_events(id)
        // so here we have to set it null
        payment.setSourceEventId(null);

        // as it belongs to payroll.bonus_installments(id) foreign key to make it null while termination settlement
        payment.setSourceInstallmentId(null);

        // Lifecycle (V0056): `status` is NOT NULL, so this dormant path
        // must name one. A settlement voucher is money already released by
        // the settlement itself, not work for the Accounts off-cycle
        // queue — PAID. `paidBy` / `paidAt` are left for the caller to set
        // if this is ever revived; nothing here fabricates them.
        payment.setStatus(OffCyclePaymentStatus.PAID);

        // now off cycle payment is full capable of linking to settlement record,
        // so we can set the settlement id here
        //payment.setSettlementId(settlement.getId());

        offCyclePaymentRepository.save(payment);
    }


    private String trimToNull(String value) {

        if (value == null) {
            return null;
        }

        value = value.trim();

        return value.isEmpty() ? null : value;
    }

}
