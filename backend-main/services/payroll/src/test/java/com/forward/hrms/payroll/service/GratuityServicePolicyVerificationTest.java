package com.forward.hrms.payroll.service;

import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.core.money.Money;
import com.forward.hrms.payroll.dto.response.GratuityDto;
import com.forward.hrms.payroll.entity.EmployeePayrollSettings;
import com.forward.hrms.payroll.entity.EmployeeSalary;
import com.forward.hrms.payroll.entity.PayrollConfig;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Correctness tests for {@link GratuityService}, pure unit (no Spring,
 * no DB — all three collaborators are mocked). Proves Audit Finding 2
 * (rounding, fixed) with an input that mathematically diverges between
 * HALF_UP and CEILING, and separately verifies the eligibility boundary
 * and date-source rules against the actual source, not assumption.
 */
class GratuityServicePolicyVerificationTest {

    private static final long EMPLOYEE_ID = 1L;

    // ------------------------------------------------------------------
    // Finding 2 — rounding mode (fixed). GratuityService.compute() now
    // routes through Money (CEILING, scale 2), matching every other
    // money computation in the settlement path.
    // ------------------------------------------------------------------
    @Test
    void compute_roundsCeiling_matchesProjectMoneyConvention() {
        // basic = 33,800.05: 33,800.05 / 26 = 1300.0019230... :
        //   HALF_UP (old behavior)  -> 1300.00 (3rd decimal digit is 1, <5)
        //   CEILING (via Money, scale 2, current behavior) -> 1300.01
        // — a value whose divide-by-26 remainder genuinely straddles the
        // HALF_UP/CEILING boundary at scale 2, so this input proves the
        // fix actually changed the computed amount, not just the code path.
        BigDecimal basic = new BigDecimal("33800.05");
        GratuityDto dto = compute(basic, LocalDate.of(2020, 1, 1), LocalDate.of(2025, 1, 1), 12, 6);

        // completedYears for exactly 60 months = 5 years, remainder 0 (< threshold 6) -> 5 years.
        assertThat(dto.completedYears()).isEqualTo(5);

        // POLICY (CEILING, scale 2, via the project's own Money helper —
        // the same helper SettlementLeaveEncashmentCalculator and
        // SettlementTerminationPolicyCalculator already correctly use):
        BigDecimal ceilingDailyRate = Money.of(basic).divide(BigDecimal.valueOf(26)).amount();
        BigDecimal policyExpected = Money.of(ceilingDailyRate)
                .multiply(BigDecimal.valueOf(30)).multiply(BigDecimal.valueOf(5)).amount();
        assertThat(dto.gratuity()).isEqualByComparingTo(policyExpected);

        // The old HALF_UP result must now differ — proves this genuinely
        // changed behavior for this input, not a coincidental match.
        BigDecimal halfUpDailyRate = basic.divide(BigDecimal.valueOf(26), RoundingMode.HALF_UP);
        BigDecimal oldHalfUpResult = halfUpDailyRate.multiply(BigDecimal.valueOf(30)).multiply(BigDecimal.valueOf(5));
        assertThat(dto.gratuity())
                .as("fixed CEILING result must differ from the old HALF_UP result for this input")
                .isNotEqualByComparingTo(oldHalfUpResult);
    }

    // ------------------------------------------------------------------
    // Eligibility boundary — exactly at, one day/month below, one above.
    // ------------------------------------------------------------------
    @Test
    void compute_exactlyAtMinimumServiceMonths_isEligible() {
        // gratuityMinServiceMonths = 12 here. ChronoUnit.MONTHS.between
        // is whole-month, so hire 2024-01-15 -> asOf 2025-01-15 = exactly 12 months.
        GratuityDto dto = compute(new BigDecimal("50000.00"),
                LocalDate.of(2024, 1, 15), LocalDate.of(2025, 1, 15), 12, 6);
        assertThat(dto.eligible()).isTrue();
        assertThat(dto.totalServiceMonths()).isEqualTo(12);
    }

    @Test
    void compute_oneDayBeforeMinimum_isNotEligible() {
        // asOf one day short of the 12-month mark -> MONTHS.between still
        // reports 11 (whole months only), so this is the "one day below"
        // case the audit asked for.
        GratuityDto dto = compute(new BigDecimal("50000.00"),
                LocalDate.of(2024, 1, 15), LocalDate.of(2025, 1, 14), 12, 6);
        assertThat(dto.eligible()).isFalse();
        assertThat(dto.gratuity()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(dto.completedYears()).isEqualTo(0);
    }

    @Test
    void compute_oneMonthAfterMinimum_isEligible_oneCompletedYear() {
        GratuityDto dto = compute(new BigDecimal("50000.00"),
                LocalDate.of(2024, 1, 15), LocalDate.of(2025, 2, 15), 12, 6);
        assertThat(dto.eligible()).isTrue();
        assertThat(dto.totalServiceMonths()).isEqualTo(13);
        // 13 months = 1 year + 1 month remainder; 1 < threshold(6) -> stays 1.
        assertThat(dto.completedYears()).isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // Partial-year threshold — exactly at, one below, one above.
    // ------------------------------------------------------------------
    @Test
    void compute_partialYearThreshold_belowThreshold_doesNotRoundUp() {
        // 12 + 5 = 17 months; remainder 5 < threshold 6 -> stays 1 year.
        GratuityDto dto = compute(new BigDecimal("50000.00"),
                LocalDate.of(2020, 1, 1), LocalDate.of(2021, 6, 1), 12, 6);
        assertThat(dto.totalServiceMonths()).isEqualTo(17);
        assertThat(dto.completedYears()).isEqualTo(1);
    }

    @Test
    void compute_partialYearThreshold_atThreshold_roundsUpToNextYear() {
        // 12 + 6 = 18 months; remainder 6 >= threshold 6 -> 2 gratuities,
        // though only 1 year is actually completed.
        GratuityDto dto = compute(new BigDecimal("50000.00"),
                LocalDate.of(2020, 1, 1), LocalDate.of(2021, 7, 1), 12, 6);
        assertThat(dto.totalServiceMonths()).isEqualTo(18);
        assertThat(dto.completedYears()).isEqualTo(1);
        assertThat(dto.noOfGratuities()).isEqualTo(2);
    }

    @Test
    void compute_sevenMonthsService_oneGratuity_zeroCompletedYears() {
        // Legacy rule: past six months pays one gratuity even though no
        // full year is completed. 50,000 / 26 = 1923.08 (CEILING) × 30 × 1.
        GratuityDto dto = compute(new BigDecimal("50000.00"),
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 10, 1), 6, 6);
        assertThat(dto.totalServiceMonths()).isEqualTo(7);
        assertThat(dto.eligible()).isTrue();
        assertThat(dto.completedYears()).isEqualTo(0);
        assertThat(dto.noOfGratuities()).isEqualTo(1);
        assertThat(dto.gratuity()).isEqualByComparingTo("57692.40");
    }

    // ------------------------------------------------------------------
    // Multiple completed years.
    // ------------------------------------------------------------------
    @Test
    void compute_multipleCompletedYears() {
        GratuityDto dto = compute(new BigDecimal("100000.00"),
                LocalDate.of(2015, 1, 1), LocalDate.of(2025, 1, 1), 12, 6);
        assertThat(dto.totalServiceMonths()).isEqualTo(120);
        assertThat(dto.completedYears()).isEqualTo(10);
    }

    // ------------------------------------------------------------------
    // Explicit gratuityStartDate overrides hireDate.
    // ------------------------------------------------------------------
    @Test
    void compute_explicitGratuityStartDate_overridesHireDate() {
        EmployeePayrollSettingsService settings = mock(EmployeePayrollSettingsService.class);
        EmployeeSalaryService salary = mock(EmployeeSalaryService.class);
        PayrollConfigService config = mock(PayrollConfigService.class);

        EmployeePayrollSettings s = new EmployeePayrollSettings();
        s.setEmployeeId(EMPLOYEE_ID);
        s.setHireDate(LocalDate.of(2018, 1, 1));       // ignored — override present
        s.setGratuityStartDate(LocalDate.of(2020, 1, 1)); // this wins
        when(settings.load(EMPLOYEE_ID)).thenReturn(s);

        PayrollConfig cfg = new PayrollConfig();
        org.springframework.test.util.ReflectionTestUtils.setField(cfg, "id", 1L);
        cfg.setGratuityMinServiceMonths(12);
        cfg.setGratuityPartialYearThresholdMonths(6);
        when(config.effectiveOn(org.mockito.ArgumentMatchers.any())).thenReturn(cfg);

        EmployeeSalary sal = new EmployeeSalary();
        sal.setBasic(new BigDecimal("50000.00"));
        when(salary.effectiveOn(EMPLOYEE_ID, LocalDate.of(2025, 1, 1))).thenReturn(sal);

        GratuityService service = new GratuityService(settings, salary, config);
        GratuityDto dto = service.compute(EMPLOYEE_ID, LocalDate.of(2025, 1, 1));

        // From gratuityStartDate (2020) to 2025 = 5 years, not from
        // hireDate (2018) which would be 7 years.
        assertThat(dto.gratuityStartDate()).isEqualTo(LocalDate.of(2020, 1, 1));
        assertThat(dto.totalServiceMonths()).isEqualTo(60);
        assertThat(dto.completedYears()).isEqualTo(5);
    }

    // ------------------------------------------------------------------
    // No gratuityStartDate override -> falls back to hireDate.
    // ------------------------------------------------------------------
    @Test
    void compute_noGratuityStartDateOverride_fallsBackToHireDate() {
        GratuityDto dto = compute(new BigDecimal("50000.00"),
                LocalDate.of(2020, 1, 1), LocalDate.of(2025, 1, 1), 12, 6);
        assertThat(dto.gratuityStartDate()).isEqualTo(LocalDate.of(2020, 1, 1));
    }

    // ------------------------------------------------------------------
    // No salary row for an ineligible employee — must not throw (uses
    // the explicit non-throwing effectiveOnOrEmpty variant).
    // ------------------------------------------------------------------
    @Test
    void compute_ineligibleEmployee_noSalaryRow_doesNotThrow() {
        EmployeePayrollSettingsService settings = mock(EmployeePayrollSettingsService.class);
        EmployeeSalaryService salary = mock(EmployeeSalaryService.class);
        PayrollConfigService config = mock(PayrollConfigService.class);

        EmployeePayrollSettings s = new EmployeePayrollSettings();
        s.setEmployeeId(EMPLOYEE_ID);
        s.setHireDate(LocalDate.of(2024, 12, 1)); // 1 month service — ineligible
        when(settings.load(EMPLOYEE_ID)).thenReturn(s);

        PayrollConfig cfg = new PayrollConfig();
        org.springframework.test.util.ReflectionTestUtils.setField(cfg, "id", 1L);
        cfg.setGratuityMinServiceMonths(12);
        cfg.setGratuityPartialYearThresholdMonths(6);
        when(config.effectiveOn(org.mockito.ArgumentMatchers.any())).thenReturn(cfg);

        // No salary row at all: effectiveOnOrEmpty returns Optional.empty(),
        // and effectiveOn (the throwing variant) is never called for an
        // ineligible employee.
        when(salary.effectiveOnOrEmpty(EMPLOYEE_ID, LocalDate.of(2025, 1, 1)))
                .thenReturn(java.util.Optional.empty());

        GratuityService service = new GratuityService(settings, salary, config);
        GratuityDto dto = service.compute(EMPLOYEE_ID, LocalDate.of(2025, 1, 1));

        assertThat(dto.eligible()).isFalse();
        assertThat(dto.gratuity()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(dto.lastBasic()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ---------------------------------------------------------------- helper

    private GratuityDto compute(BigDecimal basic, LocalDate hireDate, LocalDate asOf,
                                 int minServiceMonths, int partialYearThresholdMonths) {
        EmployeePayrollSettingsService settings = mock(EmployeePayrollSettingsService.class);
        EmployeeSalaryService salary = mock(EmployeeSalaryService.class);
        PayrollConfigService config = mock(PayrollConfigService.class);

        EmployeePayrollSettings s = new EmployeePayrollSettings();
        s.setEmployeeId(EMPLOYEE_ID);
        s.setHireDate(hireDate);
        when(settings.load(EMPLOYEE_ID)).thenReturn(s);

        PayrollConfig cfg = new PayrollConfig();
        org.springframework.test.util.ReflectionTestUtils.setField(cfg, "id", 1L);
        cfg.setGratuityMinServiceMonths(minServiceMonths);
        cfg.setGratuityPartialYearThresholdMonths(partialYearThresholdMonths);
        when(config.effectiveOn(org.mockito.ArgumentMatchers.any())).thenReturn(cfg);

        EmployeeSalary sal = new EmployeeSalary();
        sal.setBasic(basic);
        when(salary.effectiveOn(EMPLOYEE_ID, asOf)).thenReturn(sal);

        GratuityService service = new GratuityService(settings, salary, config);
        return service.compute(EMPLOYEE_ID, asOf);
    }
}
