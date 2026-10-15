package com.forward.hrms.payroll.service;

import com.forward.hrms.payroll.dto.request.AccountsReportFilter;
import com.forward.hrms.payroll.dto.response.PayslipDtoMapper;
import com.forward.hrms.payroll.dto.response.composite.PayrollResponses.PayslipAdvances;
import com.forward.hrms.payroll.entity.AnnualLeaveTransaction;
import com.forward.hrms.payroll.entity.Bank;
import com.forward.hrms.payroll.entity.EmployeeBankDetails;
import com.forward.hrms.payroll.entity.EmployeePayrollSettings;
import com.forward.hrms.payroll.entity.EmployeeSalary;
import com.forward.hrms.payroll.entity.Payslip;
import com.forward.hrms.payroll.entity.PayslipLine;
import com.forward.hrms.payroll.entity.ShortTermAdvance;
import com.forward.hrms.payroll.enums.AccountsReport;
import com.forward.hrms.payroll.enums.AccountsReportGroup;
import com.forward.hrms.payroll.enums.AccountsReportParam;
import com.forward.hrms.payroll.enums.PaymentMode;
import com.forward.hrms.payroll.enums.PaymentModeFilter;
import com.forward.hrms.payroll.repository.BankRepository;
import com.forward.hrms.payroll.repository.EmployeeBankDetailsRepository;
import com.forward.hrms.payroll.repository.EmployeePayrollSettingsRepository;
import com.forward.hrms.payroll.repository.PayslipLineRepository;
import com.forward.hrms.payroll.repository.PermanentAdvanceDeductionRepository;
import com.forward.hrms.payroll.repository.PermanentAdvanceRepository;
import com.forward.hrms.payroll.repository.SalaryReportRepository;
import com.forward.hrms.payroll.repository.ShortTermAdvanceRepository;
import com.forward.hrms.payroll.repository.projection.EmployeeAppliedAdvanceProjection;
import com.forward.hrms.payroll.service.composite.PayrollCompositesService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Salary accounts reports (A1 slip list, A2 register, A4 department summary,
 * A5 count by department, A6 bank letter, A7 bank receiving list, A9
 * annual-leave encashment).
 *
 * <p>Figures are the persisted payslip snapshot, never recalculated:
 * Due Salary = {@code payslips.basic} (after LOP), Total Payables =
 * {@code gross}, Total Deductions = {@code deductions + tax + eobi} (the
 * payslip's own total), Net = {@code net}. Allowance and rent / canteen
 * columns are the payslip's lines by component code. Basic Salary is the
 * contract basic effective at month end. Advance columns come from
 * {@link PayrollCompositesService#advances} — the same display-only block
 * the payslip prints, so they never change Total Deductions or Net.
 *
 * <p>Payment mode and bank are the employee's current payroll settings and
 * bank details (payslips don't store them). A6 and A7 are bank documents, so
 * they only ever include bank-transfer employees.
 */
@Component
@RequiredArgsConstructor
public class SalaryReportSource implements AccountsReportSource {

    /** Allowance / deduction columns read from payslip lines, column key → component code. */
    static final Map<String, String> LINE_COLUMNS = Map.of(
            "meal", "MEAL",
            "travel", "TRAVEL",
            "education", "EDUCATION",
            "special", "SPECIAL",
            "seniority", "SENIORITY",
            "accommodation", "ACCOMMODATION",
            "rent", "RENT",
            "canteen", "CANTEEN");

    private final SalaryReportRepository repo;
    private final PayslipLineRepository lines;
    private final EmployeePayrollSettingsRepository settings;
    private final EmployeeBankDetailsRepository bankDetails;
    private final BankRepository banks;
    private final ShortTermAdvanceRepository shortTermAdvances;
    private final PermanentAdvanceRepository permanentAdvances;
    private final PermanentAdvanceDeductionRepository permanentAdvanceDeductions;

    @Override
    public boolean supports(AccountsReport report) {
        return report.group() == AccountsReportGroup.SALARY;
    }

    @Override
    public List<String> captionParts(AccountsReport report, AccountsReportFilter f) {
        if (!report.accepts(AccountsReportParam.BANK) || f.bankId() == null) return List.of();
        String name = banks.findById(f.bankId()).map(Bank::getName).orElse("#" + f.bankId());
        return List.of("Bank: " + name);
    }

    @Override
    public List<AccountsReportRow> rows(AccountsReport report, AccountsReportFilter f) {
        return report == AccountsReport.A9 ? annualLeave(report, f) : salary(report, f);
    }

    // ---------- payslip-based (A2, A4, A5, A7) ----------

    private List<AccountsReportRow> salary(AccountsReport r, AccountsReportFilter f) {
        YearMonth month = f.month();
        List<Payslip> payslips = repo.findComputedPayslips(month.getYear(), month.getMonthValue()).stream()
                .filter(p -> !r.accepts(AccountsReportParam.FISCAL_YEAR) || f.fiscalYearCode() == null
                        || f.fiscalYearCode().equals(p.getFiscalYearCode()))
                .toList();
        if (payslips.isEmpty()) return List.of();

        Set<Long> ids = payslips.stream().map(Payslip::getEmployeeId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Payroll payroll = payroll(ids);
        payslips = payslips.stream().filter(p -> payroll.passes(r, f, p.getEmployeeId())).toList();
        if (payslips.isEmpty()) return List.of();

        boolean register = r == AccountsReport.A2 || r == AccountsReport.A4;
        Map<Long, List<PayslipLine>> linesByPayslip = register
                ? lines.findAllByPayslipIdInOrderByPayslipIdAscDisplayOrderAsc(
                        payslips.stream().map(Payslip::getId).toList()).stream()
                        .collect(Collectors.groupingBy(PayslipLine::getPayslipId))
                : Map.of();
        Map<Long, BigDecimal> contractBasic = register ? contractBasic(ids, month) : Map.of();
        Advances advances = register ? advances(ids, month, payslips) : null;

        List<AccountsReportRow> out = new ArrayList<>(payslips.size());
        for (Payslip p : payslips) {
            Map<String, Object> v = new LinkedHashMap<>();
            switch (r) {
                case A2, A4 -> {
                    List<PayslipLine> pl = linesByPayslip.getOrDefault(p.getId(), List.of());
                    PayslipAdvances adv = advances.of(p, pl);
                    v.put("basicSalary", contractBasic.get(p.getEmployeeId()));
                    v.put("payableDays", p.getPaidDays());
                    v.put("dueSalary", p.getBasic());
                    v.put("otAmount", p.getOtAmount());
                    v.put("otHours", p.getOtHours());
                    LINE_COLUMNS.forEach((key, code) -> v.put(key, sumLines(pl, code)));
                    v.put("totalPayables", p.getGross());
                    v.put("permanentAdvance", adv.permanentAdvance().appliedAmount());
                    v.put("permanentAdvanceDeduction", adv.permanentAdvance().deductedAmount());
                    v.put("temporaryAdvance", adv.shortTermAdvance().deductedAmount());
                    v.put("tax", p.getTax());
                    v.put("eobi", p.getEobi());
                    v.put("totalDeductions", p.getDeductions().add(p.getTax()).add(p.getEobi()));
                    v.put("netSalary", p.getNet());
                    v.put("signature", "");
                }
                case A1 -> v.put("netSalary", p.getNet());
                case A5 -> {
                    v.put("netSalary", p.getNet());
                    v.put("salaryMonth", month);
                    v.put("paymentMode", paymentModeLabel(payroll.mode(p.getEmployeeId())));
                }
                case A6, A7 -> {
                    EmployeeBankDetails b = payroll.bank(p.getEmployeeId());
                    v.put("bankName", payroll.bankName(b));
                    v.put("accountNo", b == null ? null
                            : b.getAccountNumber() != null && !b.getAccountNumber().isBlank()
                                    ? b.getAccountNumber() : b.getIban());
                    v.put("netSalary", p.getNet());
                    v.put("signature", "");
                }
                default -> throw new IllegalArgumentException("not a payslip report: " + r);
            }
            out.add(new AccountsReportRow(p.getEmployeeId(), v));
        }
        return out;
    }

    private Map<Long, BigDecimal> contractBasic(Set<Long> ids, YearMonth month) {
        Map<Long, BigDecimal> out = new HashMap<>();
        // Ordered by effective_from, so the latest row effective at month end wins.
        for (EmployeeSalary s : repo.findEffectiveOn(ids, month.atEndOfMonth())) out.put(s.getEmployeeId(), s.getBasic());
        return out;
    }

    private Advances advances(Set<Long> ids, YearMonth month, List<Payslip> payslips) {
        Map<Long, List<ShortTermAdvance>> shortTerm = shortTermAdvances
                .findAppliedForEmployeesInMonth(ids, month.getYear(), month.getMonthValue()).stream()
                .collect(Collectors.groupingBy(ShortTermAdvance::getEmployeeId));
        Map<Long, BigDecimal> applied = sumByEmployee(
                permanentAdvances.appliedTotalsByEmployeeIds(ids, month.atEndOfMonth()));
        Map<Long, Map<Long, BigDecimal>> ledgerByRun = new HashMap<>();
        for (Payslip p : payslips) {
            ledgerByRun.computeIfAbsent(p.getPayrollRunId(),
                    run -> sumByEmployee(permanentAdvanceDeductions.deductedInRunByEmployeeIds(ids, run)));
        }
        return new Advances(shortTerm, applied, ledgerByRun);
    }

    private record Advances(Map<Long, List<ShortTermAdvance>> shortTerm, Map<Long, BigDecimal> applied,
                            Map<Long, Map<Long, BigDecimal>> ledgerByRun) {
        PayslipAdvances of(Payslip p, List<PayslipLine> lines) {
            long emp = p.getEmployeeId();
            return PayrollCompositesService.advances(PayslipDtoMapper.of(p),
                    PayrollCompositesService.PayslipProvenance.isLegacyImport(p.getCreatedBy()),
                    shortTerm.getOrDefault(emp, List.of()),
                    applied.getOrDefault(emp, BigDecimal.ZERO),
                    ledgerByRun.getOrDefault(p.getPayrollRunId(), Map.of()).getOrDefault(emp, BigDecimal.ZERO),
                    lines);
        }
    }

    // ---------- annual leave (A9) ----------

    private List<AccountsReportRow> annualLeave(AccountsReport r, AccountsReportFilter f) {
        List<AnnualLeaveTransaction> txs = repo.findAnnualLeaveBetween(f.month().atDay(1), f.month().atEndOfMonth());
        if (txs.isEmpty()) return List.of();
        Payroll payroll = payroll(txs.stream().map(AnnualLeaveTransaction::getEmployeeId)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
        List<AccountsReportRow> out = new ArrayList<>(txs.size());
        for (AnnualLeaveTransaction t : txs) {
            if (!payroll.passes(r, f, t.getEmployeeId())) continue;
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("alDaysPaid", t.getPayableLeaveDays() == null ? null : BigDecimal.valueOf(t.getPayableLeaveDays()));
            v.put("alAmount", t.getGrossAnnualLeaveAmount());
            v.put("alDeduction", t.getDeductionAsLongTermAdvance() == null
                    ? BigDecimal.ZERO : t.getDeductionAsLongTermAdvance());
            v.put("netAmount", t.getNetAmount());
            out.add(new AccountsReportRow(t.getEmployeeId(), v));
        }
        return out;
    }

    // ---------- payment mode / bank ----------

    private Payroll payroll(Set<Long> ids) {
        Map<Long, PaymentMode> modes = new HashMap<>();
        for (EmployeePayrollSettings s : settings.findAllByEmployeeIdIn(ids)) modes.put(s.getEmployeeId(), s.getPaymentMode());
        Map<Long, EmployeeBankDetails> bankByEmployee = new HashMap<>();
        for (EmployeeBankDetails b : bankDetails.findByEmployeeIdIn(ids)) bankByEmployee.put(b.getEmployeeId(), b);
        Map<Long, String> bankNames = new HashMap<>();
        for (Bank b : banks.findAllByOrderByCodeAsc()) bankNames.put(b.getId(), b.getName());
        return new Payroll(modes, bankByEmployee, bankNames);
    }

    /** The employee's payroll-side placement: payment mode and salary bank. */
    private record Payroll(Map<Long, PaymentMode> modes, Map<Long, EmployeeBankDetails> bankByEmployee,
                           Map<Long, String> bankNames) {

        PaymentMode mode(long employeeId) {
            return modes.get(employeeId);
        }

        EmployeeBankDetails bank(long employeeId) {
            return bankByEmployee.get(employeeId);
        }

        String bankName(EmployeeBankDetails b) {
            return b == null ? null : bankNames.getOrDefault(b.getBankId(), "#" + b.getBankId());
        }

        /** Payment-mode (A6 / A7: always bank transfer) and bank filters; an unknown mode fails any specific mode. */
        boolean passes(AccountsReport r, AccountsReportFilter f, long employeeId) {
            PaymentModeFilter wanted = r == AccountsReport.A6 || r == AccountsReport.A7
                    ? PaymentModeFilter.BANK_TRANSFER
                    : r.accepts(AccountsReportParam.PAYMENT_MODE) ? f.paymentMode() : null;
            if (wanted != null && wanted != PaymentModeFilter.ALL) {
                PaymentMode m = mode(employeeId);
                if (m == null || !m.name().equals(wanted.name())) return false;
            }
            if (r.accepts(AccountsReportParam.BANK) && f.bankId() != null) {
                EmployeeBankDetails b = bank(employeeId);
                if (b == null || !f.bankId().equals(b.getBankId())) return false;
            }
            return true;
        }
    }

    static String paymentModeLabel(PaymentMode m) {
        if (m == null) return "—";
        return m == PaymentMode.CASH ? "Cash" : "Bank Transfer";
    }

    private static BigDecimal sumLines(List<PayslipLine> lines, String code) {
        BigDecimal sum = BigDecimal.ZERO;
        for (PayslipLine l : lines) {
            if (code.equals(l.getCode()) && l.getAmount() != null) sum = sum.add(l.getAmount());
        }
        return sum;
    }

    private static Map<Long, BigDecimal> sumByEmployee(List<EmployeeAppliedAdvanceProjection> rows) {
        Map<Long, BigDecimal> out = new HashMap<>();
        for (EmployeeAppliedAdvanceProjection p : rows) out.put(p.getEmployeeId(), p.getAppliedAmount());
        return out;
    }
}
