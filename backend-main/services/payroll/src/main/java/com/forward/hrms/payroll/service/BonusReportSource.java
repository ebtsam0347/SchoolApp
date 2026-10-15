package com.forward.hrms.payroll.service;

import com.forward.hrms.payroll.dto.request.AccountsReportFilter;
import com.forward.hrms.payroll.entity.Bank;
import com.forward.hrms.payroll.entity.BonusTransaction;
import com.forward.hrms.payroll.entity.EmployeeBankDetails;
import com.forward.hrms.payroll.enums.AccountsReport;
import com.forward.hrms.payroll.enums.AccountsReportGroup;
import com.forward.hrms.payroll.enums.AccountsReportParam;
import com.forward.hrms.payroll.enums.PaymentModeFilter;
import com.forward.hrms.payroll.repository.BankRepository;
import com.forward.hrms.payroll.repository.BonusReportRepository;
import com.forward.hrms.payroll.repository.EmployeeBankDetailsRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bonus accounts reports (E1 slip list, E2 list, E3 department summary, E5
 * bank letter, E7 permanent advance deducted from bonus) over
 * {@code bonus_transaction}.
 *
 * <p>Figures are the stored transaction, never recalculated: Basic Salary =
 * {@code basic_salary}, No. of Days = {@code no_of_days}, Due =
 * {@code percentage_salary_amount}, P.Adv deduction =
 * {@code deduction_as_long_term_advance}, RFD Advance Added =
 * {@code rfd_advance_added}, Rounded Payable = {@code net_amount}
 * (due − deduction + RFD). Payment mode is the transaction's own (fixed
 * when the bonus was computed); bank is the employee's current salary bank.
 */
@Component
@RequiredArgsConstructor
public class BonusReportSource implements AccountsReportSource {

    private final BonusReportRepository repo;
    private final EmployeeBankDetailsRepository bankDetails;
    private final BankRepository banks;

    @Override
    public boolean supports(AccountsReport report) {
        return report.group() == AccountsReportGroup.BONUS;
    }

    @Override
    public List<String> captionParts(AccountsReport report, AccountsReportFilter f) {
        if (!report.accepts(AccountsReportParam.BANK) || f.bankId() == null) return List.of();
        String name = banks.findById(f.bankId()).map(Bank::getName).orElse("#" + f.bankId());
        return List.of("Bank: " + name);
    }

    @Override
    public List<AccountsReportRow> rows(AccountsReport report, AccountsReportFilter f) {
        List<BonusTransaction> txs = repo.findComputed(f.fiscalYearCode(), f.bonusNo());
        Map<Long, EmployeeBankDetails> accounts = f.bankId() == null ? Map.of() : accounts(txs);
        // E5 is a bank letter: bank-transfer bonuses only, whatever the filter says.
        PaymentModeFilter mode = report == AccountsReport.E5 ? PaymentModeFilter.BANK_TRANSFER : f.paymentMode();
        List<AccountsReportRow> out = new ArrayList<>(txs.size());
        for (BonusTransaction t : txs) {
            if (!paymentModeMatches(mode, t)) continue;
            EmployeeBankDetails account = accounts.get(t.getEmployeeId());
            if (f.bankId() != null && (account == null || !f.bankId().equals(account.getBankId()))) continue;
            BigDecimal deduction = nz(t.getDeductionAsLongTermAdvance());
            Map<String, Object> v = new LinkedHashMap<>();
            switch (report) {
                case E5 -> {
                    v.put("accountNo", TemporaryAdvanceReportSource.accountNo(account));
                    v.put("payableAmount", t.getNetAmount());
                }
                case E1, E2, E3 -> {
                    v.put("basicSalary", t.getBasicSalary());
                    v.put("noOfDays", t.getNoOfDays() == null ? null : BigDecimal.valueOf(t.getNoOfDays()));
                    v.put("dueAmount", t.getPercentageSalaryAmount());
                    v.put("advanceDeduction", deduction);
                    v.put("rfdAdvanceAdded", nz(t.getRfdAdvanceAdded()));
                    v.put("payableAmount", t.getNetAmount());
                    v.put("signature", "");
                }
                case E7 -> {
                    if (deduction.signum() <= 0) continue;
                    v.put("amountDeducted", deduction);
                    v.put("financialPeriod", t.getFiscalYearCode());
                    v.put("bonusNo", t.getBonusNo());
                }
                default -> throw new IllegalArgumentException("not a bonus report: " + report);
            }
            out.add(new AccountsReportRow(t.getEmployeeId(), v));
        }
        return out;
    }

    private static boolean paymentModeMatches(PaymentModeFilter wanted, BonusTransaction t) {
        if (wanted == null || wanted == PaymentModeFilter.ALL) return true;
        return t.getBonusTransactionPaymentMode() != null
                && t.getBonusTransactionPaymentMode().name().equals(wanted.name());
    }

    private Map<Long, EmployeeBankDetails> accounts(List<BonusTransaction> txs) {
        Map<Long, EmployeeBankDetails> out = new HashMap<>();
        List<Long> ids = txs.stream().map(BonusTransaction::getEmployeeId).distinct().toList();
        for (EmployeeBankDetails b : bankDetails.findByEmployeeIdIn(ids)) out.put(b.getEmployeeId(), b);
        return out;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
