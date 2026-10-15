package com.forward.hrms.payroll.service.settlements;

import com.forward.hrms.payroll.entity.EmployeeFinalSettlement;
import com.forward.hrms.payroll.entity.EmployeeFinalSettlementLeaveDetail;
import com.forward.hrms.payroll.entity.LeaveEncashment;
import com.forward.hrms.payroll.enums.LeaveEncashmentStatus;
import com.forward.hrms.payroll.repository.LeaveEncashmentRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class LeaveSettlementService {

    private final LeaveEncashmentRepository repository;

    @Transactional
    public BigDecimal process(EmployeeFinalSettlement settlement) {

        if (settlement.getLeaveDetails() == null || settlement.getLeaveDetails().isEmpty()) {
            return BigDecimal.ZERO;
        }

        /*
         * Idempotency, scoped to this exact settlement — not
         * employee+month (that's a separate rule enforced only for
         * manually-created encashments in LeaveEncashmentService). This
         * guard protects against process() ever running twice for the
         * same settlement; it must not fire for a new settlement
         * created after a proper reject/cancel reversal, since reverse()
         * deletes this settlement's own rows by final_settlement_id
         * before a new one is ever created.
         */
        if (repository.existsByFinalSettlementId(settlement.getId())) {
            return BigDecimal.ZERO;
        }

        BigDecimal totalPaid = BigDecimal.ZERO;

        for (EmployeeFinalSettlementLeaveDetail detail : settlement.getLeaveDetails()) {

            /*
             * Nothing to pay.
             */
            if (detail.getEncashmentAmount() == null || detail.getEncashmentAmount().signum() <= 0) {

                continue;
            }

            LeaveEncashment encashment = new LeaveEncashment();

            encashment.setEmployeeId(settlement.getEmployeeId());

            /*
             * Final Settlement month
             */
            encashment.setCalculationMonth(settlement.getLastWorkingDay());

            /*
             * Final settlement is a single payment.
             */
            encashment.setPeriodInMonths(1);

            encashment.setDaysEncashed(detail.getPayableDays());

            encashment.setBasicSalary(settlement.getMonthlyBasicSalary());

            encashment.setAmount(detail.getEncashmentAmount());

            encashment.setPaymentStatus(LeaveEncashmentStatus.PAID);

            encashment.setPaidAt(LocalDateTime.now());

            encashment.setPayrollRunId(null);

            encashment.setDescription("Final Settlement #" + settlement.getId());

            encashment.setFinalSettlementId(settlement.getId());

            repository.save(encashment);

            totalPaid = totalPaid.add(detail.getEncashmentAmount());
        }

        return totalPaid;
    }

    /**
     * Undo {@link #process}. Deletes exactly the leave-encashment rows
     * this settlement created — nothing else is touched.
     */
    @Transactional
    public void reverse(EmployeeFinalSettlement settlement) {

        repository.deleteAllByFinalSettlementId(settlement.getId());
    }

}
