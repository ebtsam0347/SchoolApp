package com.forward.hrms.payroll.service;

import com.forward.hrms.commons.core.money.Money;
import com.forward.hrms.payroll.dto.response.GratuityDto;
import com.forward.hrms.payroll.entity.EmployeePayrollSettings;
import com.forward.hrms.payroll.entity.EmployeeSalary;
import com.forward.hrms.payroll.entity.PayrollConfig;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Read-only gratuity calculator. Formula:
 * <pre>
 *   total_service_months = months_between(hire_date, asOf)
 *   completed_years = total_service_months / 12            (whole years served)
 *   if total_service_months &lt; min_service_months → 0 gratuities, eligible=false
 *   no_of_gratuities = completed_years
 *     + (total_service_months % 12 ≥ partial_year_threshold_months ? 1 : 0)
 *   gratuity = last_basic / 26 × 30 × no_of_gratuities
 * </pre>
 * So 7 months' service pays one gratuity with zero completed years.
 */
@Service
@RequiredArgsConstructor
public class GratuityService {

    private final EmployeePayrollSettingsService settings;
    private final EmployeeSalaryService salary;
    private final PayrollConfigService payrollConfig;

    @Transactional(readOnly = true)
    public GratuityDto compute(long employeeId, LocalDate asOf) {
        LocalDate effective = asOf == null ? LocalDate.now() : asOf;
        EmployeePayrollSettings s = settings.load(employeeId);
        PayrollConfig config = payrollConfig.effectiveOn(effective);

        // Gratuity start date — explicit override on payroll settings wins,
        // falls back to hireDate. Used everywhere service-tenure is computed.
        LocalDate gratuityStart = s.getGratuityStartDate() != null
                ? s.getGratuityStartDate() : s.getHireDate();

        int totalMonths = (int) ChronoUnit.MONTHS.between(gratuityStart, effective);
        boolean eligible = totalMonths >= config.getGratuityMinServiceMonths();

        int completedYears = totalMonths / 12;
        int noOfGratuities = 0;
        BigDecimal amount = BigDecimal.ZERO;
        BigDecimal lastBasic = BigDecimal.ZERO;

        if (eligible) {
            noOfGratuities = completedYears;
            if (totalMonths % 12 >= config.getGratuityPartialYearThresholdMonths()) {
                noOfGratuities += 1;
            }
            EmployeeSalary effectiveBasic = salary.effectiveOn(employeeId, effective);
            lastBasic = effectiveBasic.getBasic();
            // Company-wide payroll rounding rule (CEILING, scale 2) lives
            // in the Money helper — this used to round HALF_UP locally,
            // disagreeing with every other money computation in the
            // settlement (Audit Finding 2).
            amount = Money.of(lastBasic)
                    .divide(BigDecimal.valueOf(26))
                    .multiply(BigDecimal.valueOf(30))
                    .multiply(BigDecimal.valueOf(noOfGratuities))
                    .amount();
        } else {
            // For visibility, surface lastBasic anyway when there's a row.
            // Use the non-throwing variant — throwing from a nested
            // @Transactional would mark this tx rollback-only and trip
            // UnexpectedRollbackException at commit, breaking the salary
            // tab for any not-yet-eligible employee without a salary row.
            lastBasic = salary.effectiveOnOrEmpty(employeeId, effective)
                    .map(EmployeeSalary::getBasic).orElse(BigDecimal.ZERO);
        }
        return new GratuityDto(employeeId, gratuityStart, s.getHireDate(), effective, totalMonths,
                eligible, completedYears, noOfGratuities, lastBasic, amount, config.getId());
    }
}
