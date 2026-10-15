package com.forward.hrms.payroll.service;

import com.forward.hrms.payroll.dto.request.AccountsReportFilter;
import com.forward.hrms.payroll.entity.Bank;
import com.forward.hrms.payroll.entity.EmployeeBankDetails;
import com.forward.hrms.payroll.entity.EmployeePayrollSettings;
import com.forward.hrms.payroll.entity.ShortTermAdvance;
import com.forward.hrms.payroll.enums.AccountsReport;
import com.forward.hrms.payroll.enums.AccountsReportGroup;
import com.forward.hrms.payroll.enums.PaymentMode;
import com.forward.hrms.payroll.repository.BankRepository;
import com.forward.hrms.payroll.repository.EmployeeBankDetailsRepository;
import com.forward.hrms.payroll.repository.EmployeePayrollSettingsRepository;
import com.forward.hrms.payroll.repository.ShortTermAdvanceReportRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Temporary (short-term) advance accounts reports over
 * {@code short_term_advances} joined to their batch.
 *
 * <p>A temporary advance is paid and recovered in full in one month — its
 * advance month ({@code target_year/target_month}). There is no stored
 * payment date and no partial-recovery ledger, so:
 * <ul>
 *   <li>B1 / B4 — lines whose advance month falls in the date range (any
 *       day of the month inside the range counts), one row per line.</li>
 *   <li>B5 — lines of the selected month, one row per line, for signing.</li>
 *   <li>B3 — the same lines as slips; B6 — those of bank-transfer employees
 *       at the chosen bank, with the account to credit.</li>
 *   <li>B2 — recovered against that month's salary: the same lines summed
 *       per employee.</li>
 * </ul>
 * Which lines count as paid is in {@link ShortTermAdvanceReportRepository}.
 */
@Component
@RequiredArgsConstructor
public class TemporaryAdvanceReportSource implements AccountsReportSource {

    private final ShortTermAdvanceReportRepository repo;
    private final EmployeePayrollSettingsRepository settings;
    private final EmployeeBankDetailsRepository bankDetails;
    private final BankRepository banks;

    @Override
    public boolean supports(AccountsReport report) {
        return report.group() == AccountsReportGroup.TEMPORARY_ADVANCE;
    }

    @Override
    public List<String> captionParts(AccountsReport report, AccountsReportFilter f) {
        if (report != AccountsReport.B6 || f.bankId() == null) return List.of();
        return List.of("Bank: " + banks.findById(f.bankId()).map(Bank::getName).orElse("#" + f.bankId()));
    }

    @Override
    public List<AccountsReportRow> rows(AccountsReport report, AccountsReportFilter f) {
        return switch (report) {
            case B1 -> lines(load(YearMonth.from(f.dateFrom()), YearMonth.from(f.dateTo())), false);
            case B4 -> lines(load(YearMonth.from(f.dateFrom()), YearMonth.from(f.dateTo())), true);
            case B5 -> lines(load(f.month(), f.month()), true);
            case B3 -> lines(load(f.month(), f.month()), false);
            case B6 -> bankTransfer(load(f.month(), f.month()), f.bankId());
            case B2 -> recovered(load(f.month(), f.month()));
            default -> throw new IllegalArgumentException("not a temporary-advance report: " + report);
        };
    }

    /**
     * B6 — the month's lines of employees paid by bank transfer whose salary
     * bank is {@code bankId}, with the account the bank should credit.
     */
    private List<AccountsReportRow> bankTransfer(List<ShortTermAdvance> advances, long bankId) {
        if (advances.isEmpty()) return List.of();
        Set<Long> ids = advances.stream().map(ShortTermAdvance::getEmployeeId).collect(Collectors.toSet());
        Map<Long, PaymentMode> modes = new HashMap<>();
        for (EmployeePayrollSettings s : settings.findAllByEmployeeIdIn(ids)) modes.put(s.getEmployeeId(), s.getPaymentMode());
        Map<Long, EmployeeBankDetails> accounts = new HashMap<>();
        for (EmployeeBankDetails b : bankDetails.findByEmployeeIdIn(ids)) accounts.put(b.getEmployeeId(), b);

        List<AccountsReportRow> out = new ArrayList<>();
        for (ShortTermAdvance s : advances) {
            EmployeeBankDetails account = accounts.get(s.getEmployeeId());
            if (modes.get(s.getEmployeeId()) != PaymentMode.BANK_TRANSFER) continue;
            if (account == null || account.getBankId() == null || account.getBankId() != bankId) continue;
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("accountNo", accountNo(account));
            values.put("advanceAmount", s.getAmount());
            out.add(new AccountsReportRow(s.getEmployeeId(), values));
        }
        return out;
    }

    /** Account number when recorded, else the IBAN. */
    static String accountNo(EmployeeBankDetails b) {
        return b.getAccountNumber() != null && !b.getAccountNumber().isBlank() ? b.getAccountNumber() : b.getIban();
    }

    private List<ShortTermAdvance> load(YearMonth from, YearMonth to) {
        return repo.findPaidBetween(key(from), key(to));
    }

    /** One row per advance line; lists meant for signing get an empty signature cell. */
    private static List<AccountsReportRow> lines(List<ShortTermAdvance> advances, boolean withSignature) {
        List<AccountsReportRow> out = new ArrayList<>(advances.size());
        for (ShortTermAdvance s : advances) {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("advanceMonth", YearMonth.of(s.getTargetYear(), s.getTargetMonth()));
            values.put("advanceAmount", s.getAmount());
            if (withSignature) values.put("signature", "");
            out.add(new AccountsReportRow(s.getEmployeeId(), values));
        }
        return out;
    }

    private static List<AccountsReportRow> recovered(List<ShortTermAdvance> advances) {
        Map<Long, BigDecimal> byEmployee = new LinkedHashMap<>();
        for (ShortTermAdvance s : advances) byEmployee.merge(s.getEmployeeId(), s.getAmount(), BigDecimal::add);
        List<AccountsReportRow> out = new ArrayList<>(byEmployee.size());
        byEmployee.forEach((emp, amount) -> {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("amountRecovered", amount);
            out.add(new AccountsReportRow(emp, values));
        });
        return out;
    }

    static int key(YearMonth m) {
        return m.getYear() * 12 + m.getMonthValue();
    }
}
