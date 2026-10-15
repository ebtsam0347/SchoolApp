package com.forward.hrms.payroll.settlement.calculator;

import com.forward.hrms.payroll.entity.PermanentAdvance;
import com.forward.hrms.payroll.entity.ShortTermAdvance;
import com.forward.hrms.payroll.enums.AdvanceStatus;
import com.forward.hrms.payroll.enums.ShortTermAdvanceStatus;
import com.forward.hrms.payroll.repository.PermanentAdvanceDeductionRepository;
import com.forward.hrms.payroll.repository.PermanentAdvanceRepository;
import com.forward.hrms.payroll.repository.ShortTermAdvanceRepository;
import com.forward.hrms.payroll.repository.projection.PermanentAdvanceSettlementProjection;
import com.forward.hrms.payroll.service.PermanentAdvanceBalances;
import com.forward.hrms.payroll.service.settlements.RfdAdvanceSettlementService;
import com.forward.hrms.payroll.settlement.context.SettlementCalculationContext;
import com.forward.hrms.payroll.settlement.context.SettlementCalculationResult;
import com.forward.hrms.payroll.settlement.context.SettlementCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SettlementAdvanceCalculator implements SettlementCalculator {

    private final PermanentAdvanceRepository permanentAdvances;

    private final PermanentAdvanceDeductionRepository deductions;

    private final ShortTermAdvanceRepository shortTermAdvances;

    private final RfdAdvanceSettlementService rfdAdvances;

    @Override
    public void calculate(
            SettlementCalculationContext ctx,
            SettlementCalculationResult result) {

        calculatePermanentAdvance(ctx.getEmployeeId(), result);

        BigDecimal shortTerm = calculateShortTermAdvance(ctx.getEmployeeId(), ctx.getLastWorkingDay());
        result.setShortTermAdvance(shortTerm);

        BigDecimal rfd = rfdAdvances.forMonthOf(ctx.getEmployeeId(), ctx.getLastWorkingDay());
        result.setRfdAdvanceAmount(rfd);
    }

    /**
     * The whole permanent-advance balance is recovered at settlement:
     * {@code total = APPROVED advances − every deduction}, the same
     * employee-level figure the payroll run uses (no monthly_deduction cap).
     * The per-advance rows are a breakdown of the open advances — the
     * employee's deductions applied oldest-first — for HR to read. If the
     * ledger is inconsistent they add up to less than the total; the total is
     * still what is recovered, so HR sees the gap instead of losing it.
     */
    private void calculatePermanentAdvance(long employeeId, SettlementCalculationResult result) {
        List<PermanentAdvance> approved = permanentAdvances.findByEmployeeIdAndStatus(employeeId, AdvanceStatus.APPROVED);
        BigDecimal deducted = deductions.employeeTotalPermanentAdvanceDeduction(employeeId);
        Map<Long, BigDecimal> recovered = PermanentAdvanceBalances.recoveredPerAdvance(approved, deducted);

        result.setPermanentAdvances(approved.stream()
                .filter(a -> a.getClosedAt() == null)
                .sorted(PermanentAdvanceBalances.OLDEST_FIRST)
                .map(a -> breakdown(a, recovered.getOrDefault(a.getId(), BigDecimal.ZERO)))
                .toList());

        BigDecimal taken = approved.stream().map(PermanentAdvance::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        result.setPermanentAdvanceTotal(taken.subtract(deducted).max(BigDecimal.ZERO));
    }

    private static PermanentAdvanceSettlementProjection breakdown(PermanentAdvance a, BigDecimal recovered) {
        return new PermanentAdvanceSettlementProjection() {
            @Override public Long getAdvanceId() { return a.getId(); }
            @Override public String getRequestCode() { return a.getRequestCode(); }
            @Override public LocalDate getReceivingDate() { return a.getReceivingDate(); }
            @Override public BigDecimal getOriginalAmount() { return a.getAmount(); }
            @Override public BigDecimal getMonthlyDeduction() { return a.getMonthlyDeduction(); }
            @Override public BigDecimal getBonusOffset() { return a.getBonusOffset(); }
            @Override public BigDecimal getAnnualLeaveOffset() { return a.getAnnualLeaveOffset(); }
            @Override public BigDecimal getRecoveredAmount() { return recovered; }
            @Override public BigDecimal getOutstandingAmount() { return a.getAmount().subtract(recovered); }
        };
    }

    // Calculating Short Term Advance
    private BigDecimal calculateShortTermAdvance(
            long employeeId,
            LocalDate lastWorkingDay) {

        List<ShortTermAdvance> rows = shortTermAdvances.findByEmployeeIdAndTargetYearAndTargetMonthAndStatusAndFinalSettlementIdIsNull(
                employeeId,
                lastWorkingDay.getYear(),
                lastWorkingDay.getMonthValue(),
                ShortTermAdvanceStatus.APPROVED
        );

        return rows.stream()
                .map(ShortTermAdvance::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }



}