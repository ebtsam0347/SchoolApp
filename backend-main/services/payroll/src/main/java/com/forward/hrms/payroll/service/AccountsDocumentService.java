package com.forward.hrms.payroll.service;

import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.core.error.UnprocessableError;
import com.forward.hrms.commons.core.error.ValidationError;
import com.forward.hrms.commons.core.error.Violation;
import com.forward.hrms.commons.reports.JasperRenderer;
import com.forward.hrms.payroll.client.AttendanceClient;
import com.forward.hrms.payroll.dto.request.AccountsReportFilter;
import com.forward.hrms.payroll.dto.response.AccountsReportColumnDto;
import com.forward.hrms.payroll.entity.Bank;
import com.forward.hrms.payroll.entity.BonusTransaction;
import com.forward.hrms.payroll.entity.Payslip;
import com.forward.hrms.payroll.enums.AccountsReport;
import com.forward.hrms.payroll.enums.PayrollErrorCode;
import com.forward.hrms.payroll.enums.ReportColumnType;
import com.forward.hrms.payroll.repository.BankRepository;
import com.forward.hrms.payroll.repository.BonusReportRepository;
import com.forward.hrms.payroll.repository.SalaryReportRepository;

import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * PDF documents of the accounts reports — slips (A1 salary, B3 temporary
 * advance, E1 bonus), bank transfer letters (A6, B6, E5), the C5
 * permanent advance ledger and the F-7 allowances calculation sheet. Every document is printed from the report's own
 * filtered rows ({@link AccountsReportService#prepareDocument}), so a PDF
 * always matches the screen and the Excel for the same filters.
 *
 * <p>Letters are addressed to the chosen bank from the {@code banks}
 * catalog, which also holds the company's own account there; the signatory
 * comes from {@code hrms.payroll.letters.*} (env
 * {@code HRMS_PAYROLL_LETTERS_SIGNATORY_NAME} / {@code …_DESIGNATION}).
 */
@Service
public class AccountsDocumentService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm", Locale.ENGLISH);
    private static final String LOGO = "jasper/logo.png";

    private final AccountsReportService reports;
    private final AccountsReportEmployees employees;
    private final BankRepository banks;
    private final SalaryReportRepository salaries;
    private final BonusReportRepository bonuses;
    private final PayslipPdfService payslipPdf;
    private final AttendanceClient attendance;
    private final JasperRenderer jasper;
    private final String signatoryName;
    private final String signatoryDesignation;

    public AccountsDocumentService(AccountsReportService reports, AccountsReportEmployees employees,
                                   BankRepository banks, SalaryReportRepository salaries,
                                   BonusReportRepository bonuses, PayslipPdfService payslipPdf,
                                   AttendanceClient attendance, JasperRenderer jasper,
                                   @Value("${hrms.payroll.letters.signatory-name:}") String signatoryName,
                                   @Value("${hrms.payroll.letters.signatory-designation:Director}")
                                   String signatoryDesignation) {
        this.reports = reports;
        this.employees = employees;
        this.banks = banks;
        this.salaries = salaries;
        this.bonuses = bonuses;
        this.payslipPdf = payslipPdf;
        this.attendance = attendance;
        this.jasper = jasper;
        this.signatoryName = signatoryName;
        this.signatoryDesignation = signatoryDesignation;
    }

    /** Slip language (legacy F18). Only the A1 salary slip has an Urdu version. */
    public enum Language { EN, UR }

    @Transactional
    public AccountsReportService.Exported render(String reportId, AccountsReportFilter f, Language language) {
        return render(reportId, f, language, false);
    }

    /** {@code sheet} prints the A1 salary slips three per A4 (other documents ignore it). */
    @Transactional
    public AccountsReportService.Exported render(String reportId, AccountsReportFilter f, Language language,
                                                 boolean sheet) {
        AccountsReport r = AccountsReport.byId(reportId).orElseThrow(() ->
                new NotFoundError(PayrollErrorCode.ACCOUNTS_REPORT_NOT_FOUND, "no accounts report " + reportId));
        if (!r.hasDocument()) {
            throw new ValidationError(PayrollErrorCode.ACCOUNTS_REPORT_NOT_A_DOCUMENT,
                    r.id() + " has no PDF document; use export",
                    List.of(new Violation("reportId", "NOT_A_DOCUMENT", "slips, letters, the ledger and F-7 only")));
        }
        AccountsReportService.Computed c = reports.prepareDocument(r.id(), f);
        if (c.rows().isEmpty()) {
            throw new NotFoundError(PayrollErrorCode.ACCOUNTS_REPORT_NOTHING_TO_PRINT,
                    "nothing to print for " + r.id() + " with these filters");
        }
        byte[] pdf = switch (r) {
            case A1 -> salarySlips(f, c, language, sheet);
            case B3 -> advanceSlips(f, c);
            case E1 -> bonusSlips(f, c);
            case A6, B6, E5 -> bankLetter(r, f, c);
            case C5 -> ledger(c);
            case F7 -> allowancesSheet(f, c);
            default -> throw new ValidationError(PayrollErrorCode.ACCOUNTS_REPORT_NOT_A_DOCUMENT,
                    r.id() + " has no PDF document", List.of());
        };
        String suffix = r == AccountsReport.A1 && language == Language.UR ? "-ur" : "";
        String filename = "%s-%s-%s%s.pdf".formatted(r.id(),
                r.title().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-"), LocalDate.now(), suffix);
        return new AccountsReportService.Exported(filename, "application/pdf", pdf);
    }

    // ---------- A1 salary slips ----------

    private byte[] salarySlips(AccountsReportFilter f, AccountsReportService.Computed c, Language language,
                               boolean sheet) {
        YearMonth m = f.month();
        Long runId = salaries.findComputedPayslips(m.getYear(), m.getMonthValue()).stream()
                .filter(p -> f.fiscalYearCode() == null || f.fiscalYearCode().equals(p.getFiscalYearCode()))
                .map(Payslip::getPayrollRunId).findFirst()
                .orElseThrow(() -> new NotFoundError(PayrollErrorCode.ACCOUNTS_REPORT_NOTHING_TO_PRINT,
                        "no computed payroll run for " + MONTH.format(m)));
        List<Long> ordered = new ArrayList<>(new LinkedHashSet<>(c.rowEmployeeIds()));
        return sheet ? payslipPdf.renderSheet(runId, ordered, language == Language.UR)
                : payslipPdf.renderPayslips(runId, ordered, language == Language.UR);
    }

    // ---------- B3 temporary advance slips ----------

    private byte[] advanceSlips(AccountsReportFilter f, AccountsReportService.Computed c) {
        Map<Long, AccountsReportEmployee> emps = employees.resolve(new LinkedHashSet<>(c.rowEmployeeIds()));
        LocalDate asOf = f.month().atEndOfMonth();
        Map<Long, String> shifts = new HashMap<>();
        List<Map<String, ?>> slips = new ArrayList<>(c.rows().size());
        for (int i = 0; i < c.rows().size(); i++) {
            Map<String, Object> row = c.rows().get(i);
            long empId = c.rowEmployeeIds().get(i);
            AccountsReportEmployee e = emps.getOrDefault(empId, AccountsReportEmployee.unknown(empId));
            BigDecimal amount = (BigDecimal) row.get("advanceAmount");
            Map<String, Object> s = new HashMap<>();
            s.put("employeeCode", e.cardNo());
            s.put("employeeName", e.name());
            s.put("grade", e.grade());
            s.put("shift", shifts.computeIfAbsent(empId, id -> attendance.shiftNameOn(id, asOf)));
            s.put("dateOfJoining", date(e.dateOfJoining()));
            s.put("department", e.department());
            s.put("designation", e.designation());
            s.put("advanceMonth", row.get("advanceMonth") instanceof YearMonth ym ? MONTH.format(ym) : null);
            s.put("amountText", money(amount));
            s.put("amountWords", amount == null ? null : AmountInWords.of(amount));
            slips.add(s);
        }
        return jasper.renderPdf("/jasper/advance-slip.jrxml", letterhead(), new JRMapCollectionDataSource(slips));
    }

    // ---------- E1 bonus slips ----------

    private byte[] bonusSlips(AccountsReportFilter f, AccountsReportService.Computed c) {
        Map<Long, BonusTransaction> byEmployee = bonuses.findComputed(f.fiscalYearCode(), f.bonusNo()).stream()
                .collect(Collectors.toMap(BonusTransaction::getEmployeeId, Function.identity(), (a, b) -> a));
        Map<Long, AccountsReportEmployee> emps = employees.resolve(new LinkedHashSet<>(c.rowEmployeeIds()));
        List<Map<String, ?>> slips = new ArrayList<>(c.rows().size());
        for (Long empId : c.rowEmployeeIds()) {
            BonusTransaction t = byEmployee.get(empId);
            if (t == null) continue;
            AccountsReportEmployee e = emps.getOrDefault(empId, AccountsReportEmployee.unknown(empId));
            Map<String, Object> s = new HashMap<>();
            s.put("employeeCode", e.cardNo());
            s.put("employeeName", e.name());
            s.put("grade", e.grade());
            s.put("dateOfJoining", date(t.getDateOfJoining() != null ? t.getDateOfJoining() : e.dateOfJoining()));
            s.put("department", e.department());
            s.put("designation", e.designation());
            s.put("financialPeriod", t.getFiscalYearCode());
            s.put("bonusNo", String.valueOf(t.getBonusNo()));
            s.put("basicText", money(t.getBasicSalary()));
            s.put("monthsDaysText", t.getNoOfMonths() + " / " + (t.getNoOfDays() == null ? "-" : t.getNoOfDays()));
            s.put("dueText", money(t.getPercentageSalaryAmount()));
            s.put("advanceText", money(nz(t.getDeductionAsLongTermAdvance())));
            s.put("rfdText", money(nz(t.getRfdAdvanceAdded())));
            s.put("netText", money(t.getNetAmount()));
            s.put("netWords", t.getNetAmount() == null ? null : AmountInWords.of(t.getNetAmount()));
            slips.add(s);
        }
        return jasper.renderPdf("/jasper/bonus-slip.jrxml", letterhead(), new JRMapCollectionDataSource(slips));
    }

    // ---------- A6 / B6 / E5 bank transfer letters ----------

    private byte[] bankLetter(AccountsReport r, AccountsReportFilter f, AccountsReportService.Computed c) {
        Bank bank = banks.findById(f.bankId()).orElseThrow(() ->
                new NotFoundError(PayrollErrorCode.ACCOUNTS_REPORT_NOTHING_TO_PRINT, "no bank " + f.bankId()));
        String companyAccount = !blank(bank.getAccountNumber()) ? bank.getAccountNumber() : bank.getIban();
        if (blank(companyAccount)) {
            throw new UnprocessableError(PayrollErrorCode.ACCOUNTS_REPORT_BANK_ACCOUNT_MISSING,
                    "bank " + bank.getName() + " has no company account number or IBAN configured");
        }
        String amountKey = r.columns().stream().filter(col -> col.type() == ReportColumnType.AMOUNT)
                .map(AccountsReportColumnDto::key).findFirst().orElseThrow();

        List<Map<String, ?>> lines = new ArrayList<>(c.rows().size());
        for (Map<String, Object> row : c.rows()) {
            Map<String, Object> l = new HashMap<>();
            l.put("serial", row.get(AccountsReportColumnDto.SR_NO));
            l.put("employeeCode", row.get(AccountsReportColumnDto.CARD_NO));
            l.put("employeeName", row.get(AccountsReportColumnDto.EMPLOYEE_NAME));
            l.put("fatherName", row.get(AccountsReportColumnDto.FATHER_NAME));
            l.put("accountNumberText", row.get("accountNo"));
            l.put("amountText", money((BigDecimal) row.get(amountKey)));
            lines.add(l);
        }
        BigDecimal total = c.totals().getOrDefault(amountKey, BigDecimal.ZERO);

        Map<String, Object> p = letterhead();
        p.put("logoPath", LOGO);
        p.put("letterDate", DATE.format(LocalDate.now()));
        p.put("bankName", bank.getName());
        p.put("bankBranch", bank.getBranch());
        p.put("bankAddress", bank.getAddress());
        p.put("companyAccountTitle", bank.getAccountTitle());
        p.put("companyAccountNumber", companyAccount);
        p.put("totalCount", c.rows().size());
        p.put("totalAmountText", money(total));
        p.put("totalAmountWords", AmountInWords.of(total));
        p.put("preparedByName", blank(signatoryName) ? null : signatoryName);
        p.put("preparedByDesignation", signatoryDesignation);
        switch (r) {
            case A6 -> {
                String month = MONTH.format(f.month());
                p.put("periodLabel", month);
                p.put("letterTitle", "SALARY BANK TRANSFER LETTER");
                p.put("subjectText", "Salary Transfer for the Month of " + month);
                p.put("purposeText", "net salary for the month of " + month);
            }
            case B6 -> {
                String month = MONTH.format(f.month());
                p.put("periodLabel", month);
                p.put("letterTitle", "TEMPORARY ADVANCE BANK TRANSFER LETTER");
                p.put("subjectText", "Temporary Advance Transfer for the Month of " + month);
                p.put("purposeText", "temporary advance for the month of " + month);
            }
            case E5 -> {
                String bonus = "Bonus No. " + f.bonusNo() + " for the Financial Year " + f.fiscalYearCode();
                p.put("periodLabel", bonus);
                p.put("letterTitle", "BONUS BANK TRANSFER LETTER");
                p.put("subjectText", "Transfer of " + bonus);
                p.put("purposeText", "bonus no. " + f.bonusNo() + " for the financial year " + f.fiscalYearCode());
            }
            default -> throw new IllegalArgumentException("not a bank letter: " + r);
        }
        return jasper.renderPdf("/jasper/bank-transfer-letter.jrxml", p, new JRMapCollectionDataSource(lines));
    }

    // ---------- C5 ledger ----------

    private byte[] ledger(AccountsReportService.Computed c) {
        Map<String, Object> h = c.header();
        if (h == null) {
            throw new UnprocessableError(PayrollErrorCode.ACCOUNTS_REPORT_NOTHING_TO_PRINT,
                    "the ledger prints one employee — give exactly one card");
        }
        List<Map<String, ?>> lines = new ArrayList<>(c.rows().size());
        for (Map<String, Object> row : c.rows()) {
            Map<String, Object> l = new HashMap<>();
            l.put("dateText", row.get("date") instanceof LocalDate d ? DATE.format(d) : null);
            l.put("description", row.get("description"));
            l.put("paidText", moneyOrBlank((BigDecimal) row.get("advancePaid")));
            l.put("recoveredText", moneyOrBlank((BigDecimal) row.get("amountRecovered")));
            l.put("balanceText", money((BigDecimal) row.get("runningBalance")));
            lines.add(l);
        }
        Map<String, Object> p = letterhead();
        for (String key : List.of(AccountsReportColumnDto.CARD_NO, AccountsReportColumnDto.EMPLOYMENT_TYPE,
                AccountsReportColumnDto.EMPLOYEE_NAME, AccountsReportColumnDto.FATHER_NAME,
                AccountsReportColumnDto.CNIC, AccountsReportColumnDto.DEPARTMENT, AccountsReportColumnDto.SECTION,
                AccountsReportColumnDto.GRADE, AccountsReportColumnDto.DESIGNATION)) {
            p.put(key, h.get(key) == null ? null : h.get(key).toString());
        }
        p.put("dateOfJoining", h.get(AccountsReportColumnDto.DATE_OF_JOINING) instanceof LocalDate d
                ? DATE.format(d) : null);
        p.put("totalPaidText", money(c.totals().get("advancePaid")));
        p.put("totalRecoveredText", money(c.totals().get("amountRecovered")));
        p.put("closingText", money(c.totals().get("runningBalance")));
        return jasper.renderPdf("/jasper/advance-ledger.jrxml", p, new JRMapCollectionDataSource(lines));
    }

    // ---------- F-7 allowances calculation sheet ----------

    private byte[] allowancesSheet(AccountsReportFilter f, AccountsReportService.Computed c) {
        Map<String, Object> p = letterhead();
        p.put("logoPath", LOGO);
        p.put("reportTitle", c.report().title());
        p.put("periodLabel", MONTH.format(f.month()));
        p.put("filtersText", c.caption());
        return AllowancesCalculationPdf.render(c, p);
    }

    // ---------- shared ----------

    private static Map<String, Object> letterhead() {
        Map<String, Object> p = new HashMap<>();
        p.put("companyName", SalaryCertificateService.COMPANY_NAME);
        p.put("companyAddress", SalaryCertificateService.COMPANY_ADDRESS);
        CurrentUser actor = CurrentUserHolder.get().orElse(null);
        p.put("generatedAt", STAMP.format(LocalDateTime.now()));
        p.put("generatedBy", actor == null ? CurrentUser.SYSTEM_USERNAME : actor.username());
        return p;
    }

    static String money(BigDecimal v) {
        return v == null ? null : new DecimalFormat("#,##0.00").format(v);
    }

    private static String moneyOrBlank(BigDecimal v) {
        return v == null || v.signum() == 0 ? null : money(v);
    }

    private static String date(LocalDate d) {
        return d == null ? null : DATE.format(d);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
