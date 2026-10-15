package com.forward.hrms.payroll.service;

import com.forward.hrms.payroll.dto.request.AccountsReportFilter;
import com.forward.hrms.payroll.dto.response.AccountsReportColumnDto;
import com.forward.hrms.payroll.entity.SalaryComponent;
import com.forward.hrms.payroll.enums.AccountsReport;
import com.forward.hrms.payroll.enums.AccountsReportGroup;
import com.forward.hrms.payroll.enums.ComponentType;
import com.forward.hrms.payroll.repository.AllowanceReportRepository;
import com.forward.hrms.payroll.repository.SalaryComponentRepository;
import com.forward.hrms.payroll.repository.projection.EmployeeAmountProjection;
import com.forward.hrms.payroll.repository.projection.EmployeeCodeAmountProjection;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Allowance accounts reports. F-1 … F-6: one salary component each. F-7
 * (allowances calculation sheet): one column per active EARNING component
 * in the catalog, plus their total. Either way the month's amount
 * per employee is payslip lines plus allowance-run transactions (see
 * {@link AllowanceReportRepository}), employees with a total above zero only.
 */
@Component
@RequiredArgsConstructor
public class AllowanceReportSource implements AccountsReportSource {

    /** Report → salary component code. */
    static final Map<AccountsReport, String> COMPONENT = new EnumMap<>(Map.of(
            AccountsReport.F1, "SPECIAL",
            AccountsReport.F2, "MEAL",
            AccountsReport.F3, "TRAVEL",
            AccountsReport.F4, "EDUCATION",
            AccountsReport.F5, "ACCOMMODATION",
            AccountsReport.F6, "SENIORITY"));

    private final AllowanceReportRepository repo;
    private final SalaryComponentRepository components;

    @Override
    public boolean supports(AccountsReport report) {
        return report.group() == AccountsReportGroup.ALLOWANCES;
    }

    /** Calculation sheet: the component columns go in before Total. */
    @Override
    public List<AccountsReportColumnDto> columns(AccountsReport report) {
        if (!isSheet(report)) return report.columns();
        List<AccountsReportColumnDto> out = new ArrayList<>();
        for (AccountsReportColumnDto col : report.columns()) {
            if (col.key().equals(AccountsReportColumnDto.TOTAL)) {
                for (SalaryComponent c : earnings()) out.add(AccountsReportColumnDto.amount(c.getCode(), c.getName()));
            }
            out.add(col);
        }
        return out;
    }

    @Override
    public List<AccountsReportRow> rows(AccountsReport report, AccountsReportFilter f) {
        return isSheet(report) ? sheetRows(f.month()) : singleRows(report, f.month());
    }

    private List<AccountsReportRow> singleRows(AccountsReport report, YearMonth m) {
        String code = COMPONENT.get(report);
        if (code == null) throw new IllegalArgumentException("not an allowance report: " + report);
        Map<Long, BigDecimal> byEmployee = new LinkedHashMap<>();
        add(byEmployee, repo.sumPayslipLines(m.getYear(), m.getMonthValue(), code));
        add(byEmployee, repo.sumAllowanceRun(m.getYear(), m.getMonthValue(), code));

        List<AccountsReportRow> out = new ArrayList<>(byEmployee.size());
        byEmployee.forEach((emp, amount) -> {
            if (amount.signum() <= 0) return;
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("amount", amount);
            out.add(new AccountsReportRow(emp, v));
        });
        return out;
    }

    /** One row per employee: every component (0 when absent) and their total. */
    private List<AccountsReportRow> sheetRows(YearMonth m) {
        List<String> codes = earnings().stream().map(SalaryComponent::getCode).toList();
        if (codes.isEmpty()) return List.of();
        Map<Long, Map<String, BigDecimal>> byEmployee = new LinkedHashMap<>();
        addByCode(byEmployee, repo.sumPayslipLinesByCode(m.getYear(), m.getMonthValue(), codes));
        addByCode(byEmployee, repo.sumAllowanceRunByCode(m.getYear(), m.getMonthValue(), codes));

        List<AccountsReportRow> out = new ArrayList<>(byEmployee.size());
        byEmployee.forEach((emp, amounts) -> {
            BigDecimal total = BigDecimal.ZERO;
            Map<String, Object> v = new LinkedHashMap<>();
            for (String code : codes) {
                BigDecimal a = amounts.getOrDefault(code, BigDecimal.ZERO);
                v.put(code, a);
                total = total.add(a);
            }
            if (total.signum() <= 0) return;
            v.put(AccountsReportColumnDto.TOTAL, total);
            out.add(new AccountsReportRow(emp, v));
        });
        return out;
    }

    private List<SalaryComponent> earnings() {
        return components.filter(ComponentType.EARNING, true);
    }

    private static boolean isSheet(AccountsReport report) {
        return report == AccountsReport.F7;
    }

    private static void add(Map<Long, BigDecimal> into, List<EmployeeAmountProjection> rows) {
        for (EmployeeAmountProjection r : rows) {
            if (r.getAmount() != null) into.merge(r.getEmployeeId(), r.getAmount(), BigDecimal::add);
        }
    }

    private static void addByCode(Map<Long, Map<String, BigDecimal>> into, List<EmployeeCodeAmountProjection> rows) {
        for (EmployeeCodeAmountProjection r : rows) {
            if (r.getAmount() == null) continue;
            into.computeIfAbsent(r.getEmployeeId(), k -> new HashMap<>())
                    .merge(r.getCode(), r.getAmount(), BigDecimal::add);
        }
    }
}
