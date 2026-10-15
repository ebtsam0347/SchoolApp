package com.forward.hrms.payroll.settlement.calculator;

import com.forward.hrms.payroll.entity.EmployeeSalary;
import com.forward.hrms.payroll.repository.EmployeeSalaryComponentRepository;
import com.forward.hrms.payroll.repository.SalaryComponentRepository;
import com.forward.hrms.payroll.service.AttendanceSnapshot;
import com.forward.hrms.payroll.settlement.context.SettlementCalculationContext;
import com.forward.hrms.payroll.settlement.context.SettlementCalculationResult;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Correctness tests for {@link SettlementSalaryCalculator}'s earned-basic
 * formula (Audit Finding 1 — fixed). Basic is now prorated over the
 * elapsed settlement period against the FULL calendar month length,
 * using {@code snapshot.paidDays()} (present + paid-leave + holiday +
 * weekend, capped at the elapsed period — built by
 * {@code SettlementServiceNew.toAttendanceSnapshot}) as the numerator,
 * matching {@code PayrollComputeService}'s own regular-payroll policy:
 * weekly-offs and holidays are inherently paid whenever they aren't LOP.
 *
 * <p>The {@link #calculate} helper below reproduces
 * {@code toAttendanceSnapshot}'s own paidDays/lopDays construction (not a
 * simplified stand-in) so these tests genuinely exercise the same shape
 * of data the calculator receives in production. Each scenario picks a
 * real {@link LocalDate} whose {@code lengthOfMonth()} matches its
 * intended month length, since the calculator now reads that directly
 * off the last working day rather than a hardcoded divisor.
 */
class SettlementSalaryCalculatorPolicyVerificationTest {

    private final SettlementSalaryCalculator calculator = new SettlementSalaryCalculator(
            emptyComponentsRepo(), emptyCatalogRepo());

    // ------------------------------------------------------------------
    // Scenario A — 30-day month (April 2026). basic=78,000, period
    // [1st,20th], present=13, paidLeave=2, unpaidLeave=1, weeklyOffs=4
    // (13+2+1+4=20 elapsed days) — the audit's own concrete example.
    // ------------------------------------------------------------------
    @Test
    void scenarioA_thirtyDayMonth_withWeekends() {
        SettlementCalculationResult result = calculate(new BigDecimal("78000.00"),
                LocalDate.of(2026, 4, 20),
                new BigDecimal("13"), new BigDecimal("2"), 4, 0);

        // paidDays = 13 present + 2 paidLeave + 0 holiday + 4 weekend = 19
        // (elapsed 20 - lopDays 1, where lopDays is the 1 unpaid-leave
        // day — weekly-offs are never LOP). 78000 * 19 / 30 = 49,400.00.
        assertThat(result.getBasicEarnedSalary()).isEqualByComparingTo("49400.00");
    }

    // ------------------------------------------------------------------
    // Scenario B — 31-day month (January 2026), identical attendance mix
    // to A. Isolates the divisor question (Step 3): the denominator now
    // moves with real month length instead of staying fixed at 26/30.
    // ------------------------------------------------------------------
    @Test
    void scenarioB_thirtyOneDayMonth_divisorRespondsToMonthLength() {
        SettlementCalculationResult result = calculate(new BigDecimal("78000.00"),
                LocalDate.of(2026, 1, 20),
                new BigDecimal("13"), new BigDecimal("2"), 4, 0);

        // 78000 * 19 / 31 = 47,806.4516... -> CEILING scale 2 = 47,806.46.
        assertThat(result.getBasicEarnedSalary()).isEqualByComparingTo("47806.46");
        assertThat(result.getBasicEarnedSalary())
                .as("a 31-day month must pay differently than a 30-day month for identical attendance")
                .isNotEqualByComparingTo("49400.00");
    }

    // ------------------------------------------------------------------
    // Scenario C — 28-day month (February 2026, non-leap), same mix.
    // ------------------------------------------------------------------
    @Test
    void scenarioC_twentyEightDayMonth_divisorRespondsToMonthLength() {
        SettlementCalculationResult result = calculate(new BigDecimal("78000.00"),
                LocalDate.of(2026, 2, 20),
                new BigDecimal("13"), new BigDecimal("2"), 4, 0);

        // 78000 * 19 / 28 = 52,928.5714... -> CEILING scale 2 = 52,928.58.
        assertThat(result.getBasicEarnedSalary()).isEqualByComparingTo("52928.58");
    }

    // ------------------------------------------------------------------
    // Scenario D / H — swapping one present day for one public holiday
    // must be pay-neutral: both are fully accounted in paidDays. Before
    // the fix, a holiday was structurally invisible and cost exactly one
    // day's basic (3,000.00) relative to an otherwise-identical present
    // day; the fix makes them equal.
    // ------------------------------------------------------------------
    @Test
    void scenarioD_holidayInPeriod_payNeutralAgainstAPresentDay() {
        // Without holiday: 14 present, 2 paid leave, 4 weekly-off (20 days, zero LOP).
        SettlementCalculationResult withoutHoliday = calculate(new BigDecimal("78000.00"),
                LocalDate.of(2026, 4, 20),
                new BigDecimal("14"), new BigDecimal("2"), 4, 0);

        // With 1 public holiday instead of 1 present day: 13 present,
        // 2 paid leave, 1 holiday, 4 weekly-off (20 days, zero LOP).
        SettlementCalculationResult withHoliday = calculate(new BigDecimal("78000.00"),
                LocalDate.of(2026, 4, 20),
                new BigDecimal("13"), new BigDecimal("2"), 4, 1);

        assertThat(withoutHoliday.getBasicEarnedSalary()).isEqualByComparingTo("52000.00");
        assertThat(withHoliday.getBasicEarnedSalary())
                .as("a holiday must pay exactly like the present day it replaced")
                .isEqualByComparingTo(withoutHoliday.getBasicEarnedSalary());
    }

    // ------------------------------------------------------------------
    // Scenario F — present + paid leave + weekend, zero LOP anywhere in
    // the period -> full pro-rata for all 20 elapsed calendar days.
    // ------------------------------------------------------------------
    @Test
    void scenarioF_presentPaidLeaveWeekend_zeroLop() {
        SettlementCalculationResult result = calculate(new BigDecimal("60000.00"),
                LocalDate.of(2026, 4, 20),
                new BigDecimal("10"), new BigDecimal("5"), 5, 0);

        // paidDays = 10 + 5 + 0 + 5 = 20 = elapsed -> zero LOP.
        // 60000 * 20 / 30 = 40,000.00 exactly.
        assertThat(result.getBasicEarnedSalary()).isEqualByComparingTo("40000.00");
    }

    // ------------------------------------------------------------------
    // Scenario G — present + unpaid leave + weekend, no paid leave.
    // ------------------------------------------------------------------
    @Test
    void scenarioG_presentUnpaidLeaveWeekend_noPaidLeave() {
        SettlementCalculationResult result = calculate(new BigDecimal("65000.00"),
                LocalDate.of(2026, 4, 20),
                new BigDecimal("11"), BigDecimal.ZERO, 4, 0);

        // paidDays = 11 + 0 + 0 + 4 = 15; lopDays = 20 - 15 = 5 (the
        // unpaid-leave days). 65000 * 15 / 30 = 32,500.00.
        assertThat(result.getBasicEarnedSalary()).isEqualByComparingTo("32500.00");
    }

    // ------------------------------------------------------------------
    // Scenario I — half-day + weekend. The half-day fraction flows
    // through paidDays unrounded; the missing half is LOP.
    // ------------------------------------------------------------------
    @Test
    void scenarioI_halfDayPlusWeekend() {
        SettlementCalculationResult result = calculate(new BigDecimal("52000.00"),
                LocalDate.of(2026, 4, 20),
                new BigDecimal("12.5"), BigDecimal.ZERO, 7, 0);

        // paidDays = 12.5 + 0 + 0 + 7 = 19.5; lopDays = 20 - 19.5 = 0.5.
        // 52000 * 19.5 / 30 = 33,800.00 exactly.
        assertThat(result.getBasicEarnedSalary()).isEqualByComparingTo("33800.00");
    }

    // ------------------------------------------------------------------
    // Scenario J — termination exactly ON a weekend day: a one-day
    // settlement period whose single day is a weekly-off, zero present,
    // zero paid leave. Before the fix this paid 0.00; a weekly-off is a
    // paid day like any other when there's no LOP on it.
    // ------------------------------------------------------------------
    @Test
    void scenarioJ_terminationExactlyOnWeekend_paysTheWeekendDay() {
        SettlementCalculationResult result = calculate(new BigDecimal("78000.00"),
                LocalDate.of(2026, 4, 1),
                BigDecimal.ZERO, BigDecimal.ZERO, 1, 0);

        // elapsed = 1 day, paidDays = 0 + 0 + 0 + 1 = 1. 78000 / 30 = 2,600.00.
        assertThat(result.getBasicEarnedSalary()).isEqualByComparingTo("2600.00");
    }

    // ------------------------------------------------------------------
    // Scenario K — termination exactly on a public holiday. Same
    // outcome as J: a holiday pays exactly like a weekend.
    // ------------------------------------------------------------------
    @Test
    void scenarioK_terminationExactlyOnHoliday_paysSameAsWeekend() {
        SettlementCalculationResult result = calculate(new BigDecimal("78000.00"),
                LocalDate.of(2026, 4, 1),
                BigDecimal.ZERO, BigDecimal.ZERO, 0, 1);

        assertThat(result.getBasicEarnedSalary()).isEqualByComparingTo("2600.00");
    }

    // ---------------------------------------------------------------- helpers

    /**
     * Drives the calculator with an {@link AttendanceSnapshot} built the
     * same way {@code SettlementServiceNew.toAttendanceSnapshot} builds
     * it in production: {@code paidDays} sums present + paid-leave +
     * holiday + weekend days, capped at the elapsed period length;
     * {@code lopDays} is whatever's left over (unpaid leave / absence).
     */
    private SettlementCalculationResult calculate(BigDecimal basic, LocalDate lastWorkingDay,
            BigDecimal present, BigDecimal paidLeaveDays, int weekendsInPeriod, int holidaysInPeriod) {

        EmployeeSalary salary = new EmployeeSalary();
        salary.setBasic(basic);

        LocalDate periodStart = lastWorkingDay.withDayOfMonth(1);
        BigDecimal elapsedDays = BigDecimal.valueOf(
                ChronoUnit.DAYS.between(periodStart, lastWorkingDay) + 1);

        BigDecimal accountedPaidDays = present.add(paidLeaveDays)
                .add(BigDecimal.valueOf(holidaysInPeriod))
                .add(BigDecimal.valueOf(weekendsInPeriod));
        BigDecimal paidDays = accountedPaidDays.min(elapsedDays);
        BigDecimal lopDays = elapsedDays.subtract(paidDays);
        if (lopDays.signum() < 0) lopDays = BigDecimal.ZERO;

        SettlementCalculationContext ctx = new SettlementCalculationContext();
        ctx.setEmployeeId(1L);
        ctx.setLastWorkingDay(lastWorkingDay);
        ctx.setSalary(salary);
        ctx.setPaidLeaveDays(paidLeaveDays);
        ctx.setAttendanceSnapshot(new AttendanceSnapshot(
                lopDays, present, BigDecimal.ZERO, elapsedDays.intValue(),
                paidDays, BigDecimal.ZERO, holidaysInPeriod, weekendsInPeriod));

        SettlementCalculationResult result = new SettlementCalculationResult();
        calculator.calculate(ctx, result);
        return result;
    }

    private EmployeeSalaryComponentRepository emptyComponentsRepo() {
        EmployeeSalaryComponentRepository repo = mock(EmployeeSalaryComponentRepository.class);
        when(repo.findEffectiveDuring(anyLong(), any(), any())).thenReturn(List.of());
        return repo;
    }

    private SalaryComponentRepository emptyCatalogRepo() {
        SalaryComponentRepository repo = mock(SalaryComponentRepository.class);
        when(repo.findAllByActiveTrue()).thenReturn(List.of());
        return repo;
    }
}
