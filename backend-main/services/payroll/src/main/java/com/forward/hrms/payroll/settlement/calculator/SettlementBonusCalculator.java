package com.forward.hrms.payroll.settlement.calculator;

import com.forward.hrms.payroll.dto.response.SettlementPreviewDto;
import com.forward.hrms.payroll.entity.FiscalYear;
import com.forward.hrms.payroll.repository.BonusEventAmountRepository;
import com.forward.hrms.payroll.repository.BonusTransactionRepository;
import com.forward.hrms.payroll.repository.FiscalYearRepository;
import com.forward.hrms.payroll.repository.OffCyclePaymentRepository;
import com.forward.hrms.payroll.settlement.context.SettlementBonusRow;
import com.forward.hrms.payroll.settlement.context.SettlementCalculationContext;
import com.forward.hrms.payroll.settlement.context.SettlementCalculationResult;
import com.forward.hrms.payroll.settlement.context.SettlementCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Month;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Bonus payable at termination.
 *
 * <p>The bonus is an accrual over the <em>current fiscal year</em>, not a
 * sum of the Eid bonus events already on the ledger. HR grants
 * {@code bonusRate} monthly basic salaries per full year (0.5, 1, 1.5, 2,
 * ...); a leaver takes only the share of the days they actually worked in
 * that fiscal year:
 *
 * <pre>
 *   since_doj  = lastWorkingDay - hireDate          (plain day difference)
 *   since_fy   = lastWorkingDay - 1 July of the FY  (plain day difference)
 *   bonus_days = min(since_doj, since_fy)
 *
 *   if since_doj &gt;= 90:  accrued = round(basic / 365 * (bonus_days + 1) * bonusRate)
 *   else:                 accrued = 0
 * </pre>
 *
 * <p>Day counts are plain subtraction; the {@code + 1} lives inside the
 * money formula so both the first and the last day are paid. The 90-day
 * eligibility gate always reads {@code since_doj}, never {@code bonus_days}
 * — a five-year employee leaving on 5 July is eligible on two days' worth.
 *
 * <p>Rounding deviates from the project's usual CEILING-at-scale-2: the
 * whole expression is evaluated as one division so nothing is rounded
 * mid-way, and the result is rounded to the nearest whole rupee, which is
 * what HR's own working produces. Worked example: basic 58,300, DOJ
 * 2025-02-17, last working day 2026-10-09, rate 2 → since_doj 599,
 * since_fy 100, bonus_days 100 → 58,300 x 101 x 2 / 365 = 32,264.657 →
 * accrued 32,265.00 (payable too, if nothing was paid during the year).
 *
 * <p>That accrual is the employee's <em>whole</em> entitlement for the
 * fiscal year, so whatever bonus has already been released to them for the
 * same year — the PAID Eid installments — is netted off it:
 *
 * <pre>
 *   payable = max(0, accrued - alreadyPaidThisFiscalYear)
 * </pre>
 *
 * <p>Without that subtraction an employee who drew an Eid bonus in, say,
 * March and left in June would be paid that same share twice. Floored at
 * zero rather than going negative: an over-payment is a recovery decision
 * for HR, not something a bonus line should silently claw back out of
 * gratuity and earned salary.
 *
 * <p>Already-paid is summed across <em>both</em> bonus subsystems, because
 * payroll has two and they record payment in different places:
 *
 * <ul>
 *   <li>{@code bonus_transaction} — the bonus runs
 *       ({@code bonus_no_settings} → compute → approve). Approval flips
 *       every row of the run to {@code PAID}; no off-cycle row is written.</li>
 *   <li>{@code off_cycle_payments} with {@code source = BONUS} — the Eid
 *       bonus-event installments ({@code BonusEventService.applyInstallment}).</li>
 * </ul>
 *
 * <p>They are disjoint — no path produces a row in both — so adding them
 * cannot double-count, and reading only one of them would miss real money.
 * {@code BonusEventAmount.paidAmount} is deliberately <em>not</em> a source:
 * nothing on the installment path writes it (its only writer is
 * {@code BonusSettlementService}, at settlement close), so it reads 0.00 /
 * PENDING for employees who have in fact been paid.
 *
 * <p>The declared Eid bonus events still ride along in the preview as a
 * read-only breakdown ({@code Bonus.events}), so HR can see event by event
 * what was declared and what was paid — they no longer drive the payable
 * amount, they explain the subtraction.
 */
@Service
@RequiredArgsConstructor
public class SettlementBonusCalculator
        implements SettlementCalculator {

    /** Fiscal year runs July–June (see CLAUDE.md / {@code fiscal_years}). */
    private static final Month FISCAL_YEAR_START_MONTH = Month.JULY;

    /** Days a full year's bonus entitlement is spread over. */
    private static final BigDecimal DAYS_PER_YEAR = BigDecimal.valueOf(365);

    /** Minimum completed service, in days, before any bonus is payable. */
    private static final long MIN_SERVICE_DAYS = 90;

    private final BonusEventAmountRepository bonusAmounts;
    private final BonusTransactionRepository bonusTransactions;
    private final OffCyclePaymentRepository offCyclePayments;
    private final FiscalYearRepository fiscalYears;

    @Override
    public void calculate(
            SettlementCalculationContext context,
            SettlementCalculationResult result) {

        SettlementPreviewDto.TerminationBonus bonus = terminationBonus(context);

        result.setTerminationBonus(bonus);
        result.setOutstandingBonus(bonus.amount());

        addDeclaredEvents(context, result);
    }

    private SettlementPreviewDto.TerminationBonus terminationBonus(
            SettlementCalculationContext context) {

        LocalDate lastWorkingDay = context.getLastWorkingDay();

        LocalDate hireDate = context.getPayrollSettings() == null
                ? null
                : context.getPayrollSettings().getHireDate();

        Optional<FiscalYear> fiscalYear = fiscalYears.findCovering(lastWorkingDay);

        LocalDate fiscalYearStart = fiscalYear
                .map(FiscalYear::getStartDate)
                .orElseGet(() -> fiscalYearStart(lastWorkingDay));

        LocalDate fiscalYearEnd = fiscalYear
                .map(FiscalYear::getEndDate)
                .orElseGet(() -> fiscalYearStart.plusYears(1).minusDays(1));

        BigDecimal basic = context.getSalary() == null || context.getSalary().getBasic() == null
                ? BigDecimal.ZERO
                : context.getSalary().getBasic();

        BigDecimal rate = context.getBonusRate() == null
                ? BigDecimal.ONE
                : context.getBonusRate();

        long serviceDays = hireDate == null
                ? 0
                : ChronoUnit.DAYS.between(hireDate, lastWorkingDay);

        long fiscalYearDays = ChronoUnit.DAYS.between(fiscalYearStart, lastWorkingDay);

        long bonusDays = Math.max(0, Math.min(serviceDays, fiscalYearDays));

        long payableDays = bonusDays + 1;

        boolean eligible = serviceDays >= MIN_SERVICE_DAYS;

        BigDecimal accrued = eligible
                ? basic.multiply(BigDecimal.valueOf(payableDays))
                        .multiply(rate)
                        .divide(DAYS_PER_YEAR, 0, RoundingMode.HALF_UP)
                        .setScale(2)
                : BigDecimal.ZERO.setScale(2);

        BigDecimal alreadyPaid = alreadyPaidThisFiscalYear(
                context.getEmployeeId(),
                fiscalYear.map(FiscalYear::getCode).orElse(null),
                fiscalYearStart,
                fiscalYearEnd);

        BigDecimal payable = accrued.subtract(alreadyPaid).max(BigDecimal.ZERO).setScale(2);

        return new SettlementPreviewDto.TerminationBonus(
                hireDate,
                fiscalYearStart,
                fiscalYearEnd,
                lastWorkingDay,
                basic.setScale(2, RoundingMode.HALF_UP),
                rate,
                serviceDays,
                fiscalYearDays,
                bonusDays,
                payableDays,
                basic.divide(DAYS_PER_YEAR, 4, RoundingMode.HALF_UP),
                eligible,
                accrued,
                alreadyPaid,
                payable
        );
    }

    /**
     * Bonus already released to this employee for the fiscal year being
     * settled, across both bonus subsystems. See the class javadoc for why
     * it takes two queries and why {@code BonusEventAmount.paidAmount} is
     * not one of them.
     *
     * <p>{@code fiscalYearCode} is null only when no {@code fiscal_years}
     * row covers the last working day; the bonus-run side is then skipped
     * rather than guessing a code, and the installment side still counts.
     */
    private BigDecimal alreadyPaidThisFiscalYear(
            long employeeId,
            String fiscalYearCode,
            LocalDate fiscalYearStart,
            LocalDate fiscalYearEnd) {

        BigDecimal fromBonusRuns = fiscalYearCode == null
                ? BigDecimal.ZERO
                : zeroIfNull(bonusTransactions.totalPaidForFiscalYear(employeeId, fiscalYearCode));

        BigDecimal fromInstallments = zeroIfNull(offCyclePayments.totalBonusPaidForFiscalYear(
                employeeId,
                fiscalYearStart,
                fiscalYearEnd));

        return fromBonusRuns.add(fromInstallments).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /**
     * 1 July of the fiscal year the given date falls in — same year for
     * July–December, previous year for January–June. Derived from the last
     * working day rather than read off the {@code CURRENT} {@code
     * fiscal_years} row — it is the fallback for when no {@code
     * fiscal_years} row covers the date at all. Note the lookup is by
     * coverage, not by the {@code CURRENT} row: a preview can legitimately
     * be run for a date outside the fiscal year that is current today.
     */
    private LocalDate fiscalYearStart(LocalDate date) {

        int year = date.getMonthValue() >= FISCAL_YEAR_START_MONTH.getValue()
                ? date.getYear()
                : date.getYear() - 1;

        return LocalDate.of(year, FISCAL_YEAR_START_MONTH, 1);
    }

    /**
     * Read-only breakdown of the Eid bonus events already declared for
     * this employee, with what each one has been paid. Informational — the
     * payable amount comes from {@link #terminationBonus} above.
     */
    private void addDeclaredEvents(
            SettlementCalculationContext context,
            SettlementCalculationResult result) {

        List<SettlementBonusRow> rows = bonusAmounts.findSettlementBonuses(
                context.getEmployeeId(),
                context.getLastWorkingDay());

        for (SettlementBonusRow row : rows) {

            BigDecimal eligible = row.amount().getFullBonus();

            BigDecimal paid = offCyclePayments.totalBonusPaidForEvent(
                    context.getEmployeeId(),
                    row.event().getId());

            BigDecimal outstanding = eligible.subtract(paid);

            if (outstanding.signum() < 0) {
                outstanding = BigDecimal.ZERO;
            }

            result.getBonuses().add(

                    new SettlementPreviewDto.OutstandingBonus(

                            row.event().getId(),

                            row.event().getName(),

                            row.event().getEventType(),

                            row.event().getReferenceDate(),

                            eligible,

                            paid,

                            outstanding
                    )
            );
        }
    }
}
