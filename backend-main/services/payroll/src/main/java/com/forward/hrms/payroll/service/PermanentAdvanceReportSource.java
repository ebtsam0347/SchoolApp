package com.forward.hrms.payroll.service;

import com.forward.hrms.payroll.dto.request.AccountsReportFilter;
import com.forward.hrms.payroll.entity.PermanentAdvance;
import com.forward.hrms.payroll.entity.PermanentAdvanceDeduction;
import com.forward.hrms.payroll.enums.AccountsReport;
import com.forward.hrms.payroll.enums.AccountsReportGroup;
import com.forward.hrms.payroll.enums.AdvanceBalanceFilter;
import com.forward.hrms.payroll.enums.AdvanceStatus;
import com.forward.hrms.payroll.enums.PermanentAdvanceDeductionSource;
import com.forward.hrms.payroll.repository.PermanentAdvanceReportRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Permanent-advance accounts reports (C1–C11) over
 * {@code permanent_advances} + {@code permanent_advance_deductions}.
 *
 * <p>Rules, each report on its own condition (legacy returned the same rows
 * for C1/C6/C7/C11):
 * <ul>
 *   <li>Paid date = {@code receiving_date}, else the disbursement date.</li>
 *   <li>Recovered = sum of {@code deducted_amount} over every deduction row,
 *       whatever its {@code source} — the same sum the payslip's P.Adv
 *       Balance uses.</li>
 *   <li>Deduction rate = sum of {@code monthly_deduction} over the
 *       employee's still-open ({@code APPROVED}) advances.</li>
 *   <li>C1 every advance paid in the month; C6 those with an annual-leave
 *       offset (amount = the offset); C11 those with a bonus offset
 *       (bonus 1 = {@code bonus_offset}, bonus 2 = {@code bonus_offset2});
 *       C7 recoveries in the month other than bonus / annual leave; C8
 *       {@code ANNUAL_LEAVE} recoveries in the month.</li>
 *   <li>C3 balance as of the range's last date; C4 as of the as-on date;
 *       C10 balance at month end with no salary deduction in that month.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class PermanentAdvanceReportSource implements AccountsReportSource {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);

    private final PermanentAdvanceReportRepository repo;

    @Override
    public boolean supports(AccountsReport report) {
        return report.group() == AccountsReportGroup.PERMANENT_ADVANCE;
    }

    @Override
    public List<AccountsReportRow> rows(AccountsReport report, AccountsReportFilter f) {
        List<PermanentAdvance> advances = repo.findPaid();
        return switch (report) {
            case C1 -> paid(advances, f.month(), false);
            case C6 -> paid(advances, f.month(), true);
            case C11 -> paidAgainstBonus(advances, f);
            case C7 -> received(repo.findAllDeductions(), f.month());
            case C8 -> deductedAgainstAnnualLeave(repo.findAllDeductions(), f.month());
            case C2 -> balance(advances, repo.findAllDeductions(), f.balance());
            case C3 -> totals(advances, repo.findAllDeductions(), f.dateTo(), true);
            case C4 -> totals(advances, repo.findAllDeductions(), f.asOnDate(), false);
            case C5 -> ledger(advances, repo.findAllDeductions());
            case C9 -> expectedReturn(advances, repo.findAllDeductions(), YearMonth.now());
            case C10 -> zeroDeduction(advances, repo.findAllDeductions(), f.month());
            default -> throw new IllegalArgumentException("not a permanent-advance report: " + report);
        };
    }

    // ---------- paid (C1, C6, C11) ----------

    private static List<AccountsReportRow> paid(List<PermanentAdvance> advances, YearMonth month,
                                                boolean againstAnnualLeave) {
        List<AccountsReportRow> out = new ArrayList<>();
        for (PermanentAdvance a : advances) {
            LocalDate d = paidDate(a);
            if (!inMonth(d, month)) continue;
            BigDecimal amount = againstAnnualLeave ? nz(a.getAnnualLeaveOffset()) : a.getAmount();
            if (againstAnnualLeave && amount.signum() <= 0) continue;
            out.add(row(a.getEmployeeId(), "transactionDate", d, "advanceAmount", amount, "signature", ""));
        }
        return out;
    }

    private static List<AccountsReportRow> paidAgainstBonus(List<PermanentAdvance> advances, AccountsReportFilter f) {
        List<AccountsReportRow> out = new ArrayList<>();
        for (PermanentAdvance a : advances) {
            LocalDate d = paidDate(a);
            if (!inMonth(d, f.month())) continue;
            if (f.fiscalYearCode() != null && !f.fiscalYearCode().equals(a.getFiscalYearCode())) continue;
            addBonusRow(out, a, d, 1, a.getBonusOffset(), f.bonusNo());
            addBonusRow(out, a, d, 2, a.getBonusOffset2(), f.bonusNo());
        }
        return out;
    }

    private static void addBonusRow(List<AccountsReportRow> out, PermanentAdvance a, LocalDate d, int bonusNo,
                                    BigDecimal amount, Integer wanted) {
        if (nz(amount).signum() <= 0) return;
        if (wanted != null && wanted != bonusNo) return;
        out.add(row(a.getEmployeeId(), "transactionDate", d, "financialPeriod", a.getFiscalYearCode(),
                "bonusNo", bonusNo, "amountAgainstBonus", amount));
    }

    // ---------- recoveries (C7, C8) ----------

    private static List<AccountsReportRow> received(List<PermanentAdvanceDeduction> deductions, YearMonth month) {
        Map<Long, BigDecimal> byEmployee = new LinkedHashMap<>();
        for (PermanentAdvanceDeduction d : deductions) {
            if (!inMonth(d.getTransactionDate(), month) || nz(d.getDeductedAmount()).signum() <= 0) continue;
            if (d.getSource() == PermanentAdvanceDeductionSource.BONUS || d.getSource() == PermanentAdvanceDeductionSource.ANNUAL_LEAVE) continue;
            byEmployee.merge(d.getEmployeeId(), d.getDeductedAmount(), BigDecimal::add);
        }
        List<AccountsReportRow> out = new ArrayList<>();
        byEmployee.forEach((emp, amount) -> out.add(row(emp, "amountReceived", amount)));
        return out;
    }

    private static List<AccountsReportRow> deductedAgainstAnnualLeave(List<PermanentAdvanceDeduction> deductions,
                                                                     YearMonth month) {
        List<AccountsReportRow> out = new ArrayList<>();
        for (PermanentAdvanceDeduction d : deductions) {
            if (!inMonth(d.getTransactionDate(), month) || d.getSource() != PermanentAdvanceDeductionSource.ANNUAL_LEAVE) continue;
            out.add(row(d.getEmployeeId(), "transactionDate", d.getTransactionDate(),
                    "amountDeducted", d.getDeductedAmount()));
        }
        return out;
    }

    // ---------- balances (C2, C3, C4, C9, C10) ----------

    private static List<AccountsReportRow> balance(List<PermanentAdvance> advances,
                                                   List<PermanentAdvanceDeduction> deductions,
                                                   AdvanceBalanceFilter filter) {
        List<AccountsReportRow> out = new ArrayList<>();
        balances(advances, deductions, null).forEach((emp, b) -> {
            if (b.issued.signum() <= 0) return;
            BigDecimal bal = b.balance();
            if (filter == AdvanceBalanceFilter.GREATER_THAN_ZERO && bal.signum() <= 0) return;
            if (filter == AdvanceBalanceFilter.EQUAL_ZERO && bal.signum() > 0) return;
            out.add(row(emp, "issuedAmount", b.issued, "recoveredAmount", b.recovered,
                    "deductionRate", b.rate, "balance", bal));
        });
        return out;
    }

    /** C3 (outstanding only) and C4 (everyone with an advance) — balance as of {@code asOf}. */
    private static List<AccountsReportRow> totals(List<PermanentAdvance> advances,
                                                  List<PermanentAdvanceDeduction> deductions,
                                                  LocalDate asOf, boolean outstandingOnly) {
        List<AccountsReportRow> out = new ArrayList<>();
        balances(advances, deductions, asOf).forEach((emp, b) -> {
            if (b.issued.signum() <= 0) return;
            if (outstandingOnly && b.balance().signum() <= 0) return;
            out.add(row(emp, "totalAdvance", b.issued, "totalDeduction", b.recovered, "balance", b.balance()));
        });
        return out;
    }

    /**
     * Months to return = balance ÷ rate, rounded up; completion month =
     * {@code current} + that many months. Rate 0 prints "—" rather than
     * dividing by zero.
     */
    static List<AccountsReportRow> expectedReturn(List<PermanentAdvance> advances,
                                                  List<PermanentAdvanceDeduction> deductions, YearMonth current) {
        List<AccountsReportRow> out = new ArrayList<>();
        balances(advances, deductions, null).forEach((emp, b) -> {
            BigDecimal bal = b.balance();
            if (bal.signum() <= 0) return;
            Integer months = b.rate.signum() > 0 ? bal.divide(b.rate, 0, RoundingMode.CEILING).intValue() : null;
            String completion = months == null ? "—" : MONTH.format(current.plusMonths(months));
            out.add(row(emp, "advanceBalance", bal, "deductionRate", b.rate,
                    "expectedMonths", months, "expectedCompletion", completion));
        });
        return out;
    }

    /** Outstanding at month end, but no salary deduction recorded in that month. */
    static List<AccountsReportRow> zeroDeduction(List<PermanentAdvance> advances,
                                                 List<PermanentAdvanceDeduction> deductions, YearMonth month) {
        Set<Long> deductedThisMonth = new HashSet<>();
        for (PermanentAdvanceDeduction d : deductions) {
            if (inMonth(d.getTransactionDate(), month) && nz(d.getDeductedAmount()).signum() > 0) {
                deductedThisMonth.add(d.getEmployeeId());
            }
        }
        List<AccountsReportRow> out = new ArrayList<>();
        balances(advances, deductions, month.atEndOfMonth()).forEach((emp, b) -> {
            if (b.balance().signum() <= 0 || deductedThisMonth.contains(emp)) return;
            out.add(row(emp, "advanceBalance", b.balance(), "deductionRate", b.rate));
        });
        return out;
    }

    // ---------- ledger (C5) ----------

    /** Every employee's ledger, chronological, running balance per employee. Advance before recovery on a tie. */
    static List<AccountsReportRow> ledger(List<PermanentAdvance> advances, List<PermanentAdvanceDeduction> deductions) {
        Map<Long, List<LedgerEntry>> byEmployee = new TreeMap<>();
        for (PermanentAdvance a : advances) {
            String desc = "Advance paid (" + a.getRequestCode() + ")"
                    + (a.getDescription() == null || a.getDescription().isBlank() ? "" : " - " + a.getDescription());
            byEmployee.computeIfAbsent(a.getEmployeeId(), k -> new ArrayList<>())
                    .add(new LedgerEntry(paidDate(a), 0, a.getId(), desc, a.getAmount(), BigDecimal.ZERO));
        }
        for (PermanentAdvanceDeduction d : deductions) {
            byEmployee.computeIfAbsent(d.getEmployeeId(), k -> new ArrayList<>())
                    .add(new LedgerEntry(d.getTransactionDate(), 1, d.getId(), recoveryDescription(d),
                            BigDecimal.ZERO, recovered(d)));
        }
        List<AccountsReportRow> out = new ArrayList<>();
        byEmployee.forEach((emp, entries) -> {
            entries.sort(Comparator.comparing(LedgerEntry::date, Comparator.nullsFirst(Comparator.naturalOrder()))
                    .thenComparingInt(LedgerEntry::kind)
                    .thenComparingLong(LedgerEntry::id));
            BigDecimal running = BigDecimal.ZERO;
            for (LedgerEntry e : entries) {
                running = running.add(e.paid).subtract(e.recovered);
                out.add(row(emp, "date", e.date, "description", e.description,
                        "advancePaid", e.paid, "amountRecovered", e.recovered, "runningBalance", running));
            }
        });
        return out;
    }

    private record LedgerEntry(LocalDate date, int kind, long id, String description,
                               BigDecimal paid, BigDecimal recovered) {}

    private static String recoveryDescription(PermanentAdvanceDeduction d) {
        if (d.getDescription() != null && !d.getDescription().isBlank()) return d.getDescription();
        return switch (d.getSource()) {
            case SALARY -> "Salary deduction";
            case BONUS -> "Bonus deduction";
            case ANNUAL_LEAVE -> "Annual leave deduction";
            case MANUAL -> "Manual deduction";
            case SETTLEMENT -> "closing entry";
            case null -> "Recovery";
        };
    }

    // ---------- shared ----------

    /** Issued / recovered / rate per employee, counting only activity on or before {@code asOf} (null = all). */
    static Map<Long, Balance> balances(List<PermanentAdvance> advances, List<PermanentAdvanceDeduction> deductions,
                                       LocalDate asOf) {
        Map<Long, Balance> out = new LinkedHashMap<>();
        for (PermanentAdvance a : advances) {
            LocalDate d = paidDate(a);
            if (asOf != null && (d == null || d.isAfter(asOf))) continue;
            Balance b = out.computeIfAbsent(a.getEmployeeId(), k -> new Balance());
            b.issued = b.issued.add(a.getAmount());
            if (a.getStatus() == AdvanceStatus.APPROVED && a.getClosedAt() == null) b.rate = b.rate.add(nz(a.getMonthlyDeduction()));
        }
        for (PermanentAdvanceDeduction d : deductions) {
            LocalDate t = d.getTransactionDate();
            if (asOf != null && (t == null || t.isAfter(asOf))) continue;
            Balance b = out.computeIfAbsent(d.getEmployeeId(), k -> new Balance());
            b.recovered = b.recovered.add(recovered(d));
        }
        return out;
    }

    static final class Balance {
        BigDecimal issued = BigDecimal.ZERO;
        BigDecimal recovered = BigDecimal.ZERO;
        BigDecimal rate = BigDecimal.ZERO;

        BigDecimal balance() {
            return issued.subtract(recovered);
        }
    }

    static LocalDate paidDate(PermanentAdvance a) {
        if (a.getReceivingDate() != null) return a.getReceivingDate();
        return a.getDisbursedAt() == null ? null : a.getDisbursedAt().toLocalDate();
    }

    static BigDecimal recovered(PermanentAdvanceDeduction d) {
        return nz(d.getDeductedAmount());
    }

    private static boolean inMonth(LocalDate d, YearMonth month) {
        return d != null && month != null && YearMonth.from(d).equals(month);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static AccountsReportRow row(long employeeId, Object... kv) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) values.put((String) kv[i], kv[i + 1]);
        return new AccountsReportRow(employeeId, values);
    }
}
