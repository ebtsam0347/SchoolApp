package com.forward.hrms.payroll.service;

import com.forward.hrms.payroll.entity.EmployeeSalary;
import com.forward.hrms.payroll.entity.PayrollRun;
import com.forward.hrms.payroll.entity.Payslip;
import com.forward.hrms.payroll.repository.EmployeeSalaryRepository;
import com.forward.hrms.payroll.repository.PayrollRunRepository;
import com.forward.hrms.payroll.repository.PayslipRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Payroll analytics — monthly cost trend (last N months), cost per
 * head, and salary distribution by grade. All read directly from
 * {@code payslips} / {@code employee_salaries}; no warehouse.
 */
@Service
@RequiredArgsConstructor
public class PayrollAnalyticsService {

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final int DEFAULT_MONTHS = 24;
    private static final int MAX_MONTHS = 36;

    private final PayrollRunRepository runs;
    private final PayslipRepository payslips;
    private final EmployeeSalaryRepository salaries;

    @Transactional(readOnly = true)
    public CostTrendDto costTrend(int months) {
        int n = clamp(months);
        YearMonth current = YearMonth.now();
        YearMonth start = current.minusMonths(n - 1L);

        // Group runs by (year, month) so multiple runs in a single
        // calendar month roll up cleanly.
        Map<YearMonth, List<PayrollRun>> runsByMonth = new HashMap<>();
        for (PayrollRun r : runs.findAll()) {
            YearMonth ym = YearMonth.of(r.getPeriodYear(), r.getPeriodMonth());
            if (ym.isBefore(start) || ym.isAfter(current)) continue;
            runsByMonth.computeIfAbsent(ym, k -> new ArrayList<>()).add(r);
        }

        List<CostMonth> series = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            YearMonth ym = start.plusMonths(i);
            List<PayrollRun> ms = runsByMonth.getOrDefault(ym, List.of());
            BigDecimal gross = BigDecimal.ZERO;
            BigDecimal net = BigDecimal.ZERO;
            BigDecimal eobi = BigDecimal.ZERO;
            BigDecimal pessi = BigDecimal.ZERO;
            BigDecimal tax = BigDecimal.ZERO;
            long employeeCount = 0;
            for (PayrollRun pr : ms) {
                PayslipRepository.RunTotals t = payslips.totalsForRun(pr.getId(), null, null);
                if (t == null) continue;
                gross = gross.add(t.gross());
                net = net.add(t.net());
                eobi = eobi.add(t.eobi());
                pessi = pessi.add(t.pessiEmployer());
                tax = tax.add(t.tax());
                employeeCount += t.employeeCount();
            }
            BigDecimal total = gross.add(pessi); // employer-side cost = gross + employer PESSI
            BigDecimal costPerHead = employeeCount == 0 ? BigDecimal.ZERO
                    : total.divide(BigDecimal.valueOf(employeeCount), 2, RoundingMode.HALF_UP);
            series.add(new CostMonth(ym.format(MONTH_FMT), employeeCount,
                    gross, eobi, pessi, tax, net, total, costPerHead));
        }
        return new CostTrendDto(n, series);
    }

    @Transactional(readOnly = true)
    public SalaryDistributionDto salaryDistribution() {
        // Current snapshot — every employee's open (effective_to=null)
        // basic. Grouping by gradeId from the salary row alone is
        // ambiguous (grade lives on employees), so caller-side enrichment
        // happens in the BFF if needed; here we group by the salary
        // record's snapshot fields (none today) and fall back to the
        // employee-id-keyed map. To keep the call useful without an
        // employee-service round-trip we return a single grade=null
        // bucket with the company-wide percentile spread + the raw
        // basics list (capped) so the FE can re-bucket if a grade map
        // is supplied.
        List<BigDecimal> currents = new ArrayList<>();
        for (EmployeeSalary s : salaries.findAll()) {
            if (s.getEffectiveTo() != null) continue;
            currents.add(s.getBasic());
        }
        Collections.sort(currents);
        Percentiles p = currents.isEmpty()
                ? new Percentiles(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO, 0)
                : computePercentiles(currents);
        return new SalaryDistributionDto(p, p.count() <= 5000 ? currents : List.of());
    }

    private static Percentiles computePercentiles(List<BigDecimal> sorted) {
        return new Percentiles(
                sorted.get(0),
                pick(sorted, 0.25),
                pick(sorted, 0.50),
                pick(sorted, 0.75),
                sorted.get(sorted.size() - 1),
                sorted.size());
    }

    private static BigDecimal pick(List<BigDecimal> sorted, double q) {
        int idx = (int) Math.round(q * (sorted.size() - 1));
        return sorted.get(idx);
    }

    private static int clamp(int n) {
        if (n <= 0) return DEFAULT_MONTHS;
        return Math.min(n, MAX_MONTHS);
    }

    @SuppressWarnings("unused")
    private static <T> T noop(T t) { return t; }

    @SuppressWarnings("unused")
    private static List<Payslip> orEmpty(List<Payslip> in) {
        return in == null ? List.of() : in;
    }

    @SuppressWarnings("unused")
    private static Pageable unpaged() { return Pageable.unpaged(); }

    public record CostTrendDto(int months, List<CostMonth> series) {}
    public record CostMonth(
            String month, long employeeCount,
            BigDecimal gross, BigDecimal eobi, BigDecimal pessiEmployer,
            BigDecimal tax, BigDecimal net,
            BigDecimal totalCost, BigDecimal costPerHead) {}

    public record SalaryDistributionDto(Percentiles percentiles, List<BigDecimal> rawBasics) {}
    public record Percentiles(
            BigDecimal min, BigDecimal p25, BigDecimal median, BigDecimal p75, BigDecimal max,
            int count) {}
}
