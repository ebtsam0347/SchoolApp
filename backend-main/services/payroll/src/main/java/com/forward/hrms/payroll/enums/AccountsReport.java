package com.forward.hrms.payroll.enums;

import com.forward.hrms.payroll.dto.response.AccountsReportColumnDto;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.forward.hrms.payroll.dto.response.AccountsReportColumnDto.amount;
import static com.forward.hrms.payroll.dto.response.AccountsReportColumnDto.amountNoTotal;
import static com.forward.hrms.payroll.dto.response.AccountsReportColumnDto.cardNo;
import static com.forward.hrms.payroll.dto.response.AccountsReportColumnDto.date;
import static com.forward.hrms.payroll.dto.response.AccountsReportColumnDto.decimal;
import static com.forward.hrms.payroll.dto.response.AccountsReportColumnDto.fatherName;
import static com.forward.hrms.payroll.dto.response.AccountsReportColumnDto.grade;
import static com.forward.hrms.payroll.dto.response.AccountsReportColumnDto.dateOfJoining;
import static com.forward.hrms.payroll.dto.response.AccountsReportColumnDto.department;
import static com.forward.hrms.payroll.dto.response.AccountsReportColumnDto.designation;
import static com.forward.hrms.payroll.dto.response.AccountsReportColumnDto.employeeName;
import static com.forward.hrms.payroll.dto.response.AccountsReportColumnDto.employeeStatus;
import static com.forward.hrms.payroll.dto.response.AccountsReportColumnDto.integer;
import static com.forward.hrms.payroll.dto.response.AccountsReportColumnDto.month;
import static com.forward.hrms.payroll.dto.response.AccountsReportColumnDto.section;
import static com.forward.hrms.payroll.dto.response.AccountsReportColumnDto.signature;
import static com.forward.hrms.payroll.dto.response.AccountsReportColumnDto.text;

/**
 * Registry of accounts reports — the single source of truth for each
 * report's title, group, filters (required / optional) and columns in
 * display order. Validation, the filter panel, sorting whitelist and export
 * headers all read from here. Constant names are the legacy report ids
 * (A1 … H4) so accounts staff and the mapping doc can cross-reference.
 *
 * <p>Only implemented reports are listed; a group lands here when its data
 * source does.
 */
public enum AccountsReport {

    // ---------- A. Salary ----------
    // From the persisted payslip snapshot (payslips + payslip_lines) of the
    // month's payroll run. Bonus, annual-leave amount, arrears, incentive
    // and worker welfare are not part of salary in this system and have
    // their own reports. Advance columns are the
    // payslip's display-only advances block — never part of Total
    // Deductions or Net. A3 day/night waits for a shift day/night flag;
    // A8 email is out of scope. SLIP / LETTER reports list their employees
    // on the data endpoint and print through the document endpoint.

    A1("Salary Slip", AccountsReportGroup.SALARY, AccountsReportOutput.SLIP,
            salaryRequired(), with(salaryOptional(), AccountsReportParam.SLIP_LANGUAGE),
            cardNo(), employeeName(), department(), designation(), amount("netSalary", "Net Pay")),

    A2("Salary Register", AccountsReportGroup.SALARY, AccountsReportOutput.LIST,
            salaryRequired(), salaryOptional(),
            cardNo(), employeeName(), department(), designation(),
            amount("basicSalary", "Basic Salary"), decimal("payableDays", "Payable Days"),
            amount("dueSalary", "Due Salary"), amount("otAmount", "Overtime Amount"),
            decimal("otHours", "Overtime Hours"),
            amount("meal", "Meal Allowance"), amount("travel", "Travel Allowance"),
            amount("education", "Education Allowance"), amount("special", "Special Allowance"),
            amount("seniority", "Seniority Allowance"), amount("accommodation", "Accommodation Allowance"),
            amount("totalPayables", "Total Payables"),
            amount("permanentAdvance", "Permanent Advance"),
            amount("permanentAdvanceDeduction", "Permanent Advance Deduction"),
            amount("temporaryAdvance", "Temporary Advance"),
            amount("tax", "Tax"), amount("eobi", "EOBI"), amount("rent", "Rent"),
            amount("canteen", "Canteen Deduction"),
            amount("totalDeductions", "Total Deductions"), amount("netSalary", "Net Salary"), signature()),

    A4("Salary Summary - Department Wise", AccountsReportGroup.SALARY, AccountsReportOutput.SUMMARY,
            salaryRequired(),
            EnumSet.of(AccountsReportParam.PAYMENT_MODE, AccountsReportParam.DEPARTMENT,
                    AccountsReportParam.EMP_STATUS),
            department(), integer("employeeCount", "No. of Employees"),
            amount("basicSalary", "Basic Salary"), amount("dueSalary", "Due Salary"),
            amount("otAmount", "Overtime Amount"), amount("meal", "Meal Allowance"),
            amount("travel", "Travel Allowance"), amount("special", "Special Allowance"),
            amount("education", "Education Allowance"), amount("totalPayables", "Total Payables"),
            amount("temporaryAdvance", "Temporary Advance"),
            amount("permanentAdvanceDeduction", "Permanent Advance"),
            amount("eobi", "EOBI"), amount("tax", "Tax"), amount("rent", "Rent"),
            amount("totalDeductions", "Total Deductions"), amount("netSalary", "Net Salary")),

    A5("Salary Count by Department", AccountsReportGroup.SALARY, AccountsReportOutput.SUMMARY,
            EnumSet.of(AccountsReportParam.MONTH, AccountsReportParam.PAYMENT_MODE),
            EnumSet.of(AccountsReportParam.DEPARTMENT),
            department(), integer("employeeCount", "Employee Count"), amount("netSalary", "Payable Salary"),
            month("salaryMonth", "Salary Month"), text("paymentMode", "Payment Mode")),

    A6("Salary Bank Transfer Letter", AccountsReportGroup.SALARY, AccountsReportOutput.LETTER,
            EnumSet.of(AccountsReportParam.FISCAL_YEAR, AccountsReportParam.MONTH, AccountsReportParam.BANK),
            letterOptional(salaryOptional()),
            cardNo(), employeeName(), fatherName(), text("accountNo", "Bank Account No"),
            amount("netSalary", "Net Amount")),

    A7("Bank Salary Receiving List", AccountsReportGroup.SALARY, AccountsReportOutput.GROUPED_LIST, "bankName",
            salaryRequired(), salaryOptional(),
            cardNo(), employeeName(), fatherName(), department(), designation(),
            text("bankName", "Bank Name"), text("accountNo", "Bank Account No"),
            amount("netSalary", "Net Salary"), signature()),

    A9("Annual Leave Encashment Calculation", AccountsReportGroup.SALARY, AccountsReportOutput.LIST,
            EnumSet.of(AccountsReportParam.MONTH), salaryOptional(),
            cardNo(), employeeName(), department(),
            decimal("alDaysPaid", "Annual Leave Days Paid"), amount("alAmount", "Annual Leave Amount"),
            amount("alDeduction", "Annual Leave Deduction"), amount("netAmount", "Net Amount")),

    // ---------- B. Temporary (short-term) advance ----------
    // No stored advance date: the advance month (target year/month) is the
    // legacy adv_month and stands in for it. Branch is not on B3 (no source).

    B1("Temporary Advance Paid", AccountsReportGroup.TEMPORARY_ADVANCE, AccountsReportOutput.LIST,
            advanceRequired(AccountsReportParam.DATE_RANGE), orgWithoutCardList(),
            cardNo(), employeeName(), department(), section(), designation(),
            month("advanceMonth", "Advance Month"), amount("advanceAmount", "Advance Amount"), employeeStatus()),

    B2("Temporary Advance Received Against Salary", AccountsReportGroup.TEMPORARY_ADVANCE, AccountsReportOutput.LIST,
            advanceRequired(AccountsReportParam.MONTH), orgWithoutCardList(),
            cardNo(), employeeName(), department(), designation(),
            amount("amountRecovered", "Advance Amount Recovered"), employeeStatus()),

    B3("Temporary Advance Slip", AccountsReportGroup.TEMPORARY_ADVANCE, AccountsReportOutput.SLIP,
            advanceRequired(AccountsReportParam.MONTH), orgWithoutCardList(),
            cardNo(), employeeName(), department(), designation(),
            month("advanceMonth", "Advance Month"), amount("advanceAmount", "Advance Amount")),

    B4("Temporary Advance Received List", AccountsReportGroup.TEMPORARY_ADVANCE, AccountsReportOutput.LIST,
            advanceRequired(AccountsReportParam.DATE_RANGE), orgWithoutCardList(),
            cardNo(), employeeName(), department(), section(), designation(),
            month("advanceMonth", "Advance Month"), amount("advanceAmount", "Advance Amount"), signature()),

    B5("Temporary Advance Receiving List", AccountsReportGroup.TEMPORARY_ADVANCE, AccountsReportOutput.LIST,
            advanceRequired(AccountsReportParam.MONTH), orgWithoutCardList(),
            cardNo(), employeeName(), department(), section(), designation(),
            month("advanceMonth", "Advance Month"), amount("advanceAmount", "Advance Amount"), signature()),

    B6("Temporary Advance Bank Transfer Letter", AccountsReportGroup.TEMPORARY_ADVANCE,
            AccountsReportOutput.LETTER,
            advanceRequired(AccountsReportParam.MONTH, AccountsReportParam.BANK), orgWithoutCardList(),
            cardNo(), employeeName(), fatherName(), text("accountNo", "Bank Account No"),
            amount("advanceAmount", "Amount")),

    // ---------- C. Permanent advance ----------

    C1("Permanent Advance Paid", AccountsReportGroup.PERMANENT_ADVANCE, AccountsReportOutput.LIST,
            advanceRequired(AccountsReportParam.MONTH), org(),
            cardNo(), employeeName(), department(), section(), designation(),
            date("transactionDate", "Transaction Date"), amount("advanceAmount", "Advance Amount"), signature()),

    C2("Permanent Advance Balance", AccountsReportGroup.PERMANENT_ADVANCE, AccountsReportOutput.LIST,
            advanceRequired(AccountsReportParam.BALANCE), org(),
            cardNo(), employeeName(), designation(), department(),
            amount("issuedAmount", "Issued Amount"), amount("recoveredAmount", "Recovered Amount"),
            amount("deductionRate", "Deduction Rate"), amount("balance", "Balance"), employeeStatus()),

    C3("Permanent Advance Balance by Date", AccountsReportGroup.PERMANENT_ADVANCE, AccountsReportOutput.LIST,
            advanceRequired(AccountsReportParam.DATE_RANGE), org(),
            cardNo(), employeeName(), department(),
            amount("totalAdvance", "Total Advance Amount"), amount("totalDeduction", "Total Deduction Amount"),
            amount("balance", "Balance")),

    C4("Total Permanent Advance Paid", AccountsReportGroup.PERMANENT_ADVANCE, AccountsReportOutput.LIST,
            advanceRequired(AccountsReportParam.AS_ON_DATE), org(),
            cardNo(), employeeName(), department(),
            amount("totalAdvance", "Total Advance Amount"), amount("totalDeduction", "Total Deduction"),
            amount("balance", "Balance")),

    C5("Permanent Advance Ledger", AccountsReportGroup.PERMANENT_ADVANCE, AccountsReportOutput.LEDGER,
            advanceRequired(AccountsReportParam.CARD_LIST), org(),
            date("date", "Date"), text("description", "Description"),
            amount("advancePaid", "Advance Paid"), amount("amountRecovered", "Amount Recovered"),
            amountNoTotal("runningBalance", "Running Balance")),

    C6("Permanent Advance Paid Against Annual Leaves", AccountsReportGroup.PERMANENT_ADVANCE, AccountsReportOutput.LIST,
            advanceRequired(AccountsReportParam.MONTH), org(),
            cardNo(), employeeName(), department(), section(), designation(),
            date("transactionDate", "Transaction Date"), amount("advanceAmount", "Advance Amount"), signature()),

    C7("Permanent Advance Received", AccountsReportGroup.PERMANENT_ADVANCE, AccountsReportOutput.LIST,
            advanceRequired(AccountsReportParam.MONTH), org(),
            cardNo(), employeeName(), department(), section(), designation(),
            amount("amountReceived", "Amount Received"), employeeStatus()),

    C8("Permanent Advance Deducted Against Annual Leaves", AccountsReportGroup.PERMANENT_ADVANCE,
            AccountsReportOutput.LIST,
            advanceRequired(AccountsReportParam.MONTH), org(),
            cardNo(), employeeName(), department(), section(), designation(),
            date("transactionDate", "Transaction Date"), amount("amountDeducted", "Amount Deducted")),

    C9("Permanent Advance Expected Return", AccountsReportGroup.PERMANENT_ADVANCE, AccountsReportOutput.LIST,
            advanceRequired(), org(),
            cardNo(), employeeName(), department(), section(), designation(), dateOfJoining(),
            amount("advanceBalance", "Advance Balance"), amount("deductionRate", "Deduction Rate"),
            integer("expectedMonths", "Expected Months to Return"),
            text("expectedCompletion", "Expected Completion Month")),

    C10("Permanent Advance Employees With Zero Deduction", AccountsReportGroup.PERMANENT_ADVANCE,
            AccountsReportOutput.LIST,
            advanceRequired(AccountsReportParam.MONTH), org(),
            cardNo(), employeeName(), department(), designation(),
            amount("advanceBalance", "Advance Balance"), amount("deductionRate", "Deduction Rate")),

    C11("Permanent Advance Paid Against Bonus", AccountsReportGroup.PERMANENT_ADVANCE, AccountsReportOutput.LIST,
            advanceRequired(AccountsReportParam.MONTH),
            org(AccountsReportParam.FISCAL_YEAR, AccountsReportParam.BONUS_NO),
            cardNo(), employeeName(), section(), designation(),
            date("transactionDate", "Transaction Date"), text("financialPeriod", "Financial Period"),
            integer("bonusNo", "Bonus No"), amount("amountAgainstBonus", "Amount Against Bonus"),
            employeeStatus()),

    // ---------- E. Bonus ----------
    // From bonus_transaction of a computed bonus (fiscal year + bonus no).
    // Legacy "Gross Salary" is the basic salary the bonus was computed on,
    // so it is labelled Basic Salary. The P.Adv deduction is the bonus's own
    // column (0 until the bonus engine applies it). RFD on a bonus is an
    // addition (cash rounded up to a 500 note), not a deduction. E4 needs its
    // contribution rule; E6 email is out of scope.

    E1("Bonus Slip", AccountsReportGroup.BONUS, AccountsReportOutput.SLIP,
            bonusRequired(), bonusOptional(),
            cardNo(), employeeName(), department(), designation(),
            amount("payableAmount", "Net Payable Bonus")),

    E2("Bonus List", AccountsReportGroup.BONUS, AccountsReportOutput.LIST,
            bonusRequired(), bonusOptional(),
            cardNo(), employeeName(), dateOfJoining(), department(), designation(),
            amount("basicSalary", "Basic Salary"), decimal("noOfDays", "No. of Days"),
            amount("dueAmount", "Due Amount"), amount("advanceDeduction", "Deduction (Permanent Advance)"),
            amount("payableAmount", "Rounded Payable Amount"), signature()),

    E3("Bonus Summary - Department Wise", AccountsReportGroup.BONUS, AccountsReportOutput.SUMMARY,
            bonusRequired(), bonusOptional(),
            department(), integer("employeeCount", "No. of Employees"),
            amount("basicSalary", "Basic Salary"), amount("dueAmount", "Due Bonus"),
            amount("advanceDeduction", "Permanent Advance Deduction"),
            amount("rfdAdvanceAdded", "RFD Advance Added"), amount("payableAmount", "Net Payable")),

    E5("Bonus Bank Transfer Letter", AccountsReportGroup.BONUS, AccountsReportOutput.LETTER,
            with(bonusRequired(), AccountsReportParam.BANK), letterOptional(bonusOptional()),
            cardNo(), employeeName(), fatherName(), text("accountNo", "Bank Account No"),
            amount("payableAmount", "Amount")),

    E7("Permanent Advance Deducted from Bonus", AccountsReportGroup.BONUS, AccountsReportOutput.LIST,
            bonusRequired(), bonusOptional(),
            cardNo(), employeeName(), department(), section(), designation(),
            amount("amountDeducted", "Amount Deducted"), text("financialPeriod", "Financial Period"),
            integer("bonusNo", "Bonus No")),

    // ---------- F. Allowances ----------
    // Ids are "F-1" … "F-6" (see id()) so they can't be mistaken for the
    // F1–F19 filter codes. Month total per employee = the allowance's payslip
    // lines (legacy months) + its allowance-run transactions (paid apart from
    // salary); only employees with an amount > 0. Meal has no per-day figure
    // in this system, so the legacy per-day column is not carried.

    F1("Special Allowance", AccountsReportGroup.ALLOWANCES, AccountsReportOutput.LIST,
            EnumSet.of(AccountsReportParam.MONTH), allowanceOptional(),
            cardNo(), employeeName(), department(), section(), designation(),
            amount("amount", "Special Allowance")),

    F2("Meal Allowance", AccountsReportGroup.ALLOWANCES, AccountsReportOutput.LIST,
            EnumSet.of(AccountsReportParam.MONTH), allowanceOptional(),
            cardNo(), employeeName(), department(), section(), designation(),
            amount("amount", "Meal Allowance")),

    F3("Travel Allowance", AccountsReportGroup.ALLOWANCES, AccountsReportOutput.LIST,
            EnumSet.of(AccountsReportParam.MONTH), allowanceOptional(),
            cardNo(), employeeName(), department(), section(), designation(),
            amount("amount", "Travel Allowance")),

    F4("Education Allowance", AccountsReportGroup.ALLOWANCES, AccountsReportOutput.LIST,
            EnumSet.of(AccountsReportParam.MONTH), allowanceOptional(),
            cardNo(), employeeName(), department(), section(), designation(),
            amount("amount", "Education Allowance")),

    F5("Accommodation Allowance", AccountsReportGroup.ALLOWANCES, AccountsReportOutput.LIST,
            EnumSet.of(AccountsReportParam.MONTH), allowanceOptional(),
            cardNo(), employeeName(), department(), section(), designation(),
            amount("amount", "Accommodation Allowance")),

    F6("Seniority Allowance", AccountsReportGroup.ALLOWANCES, AccountsReportOutput.LIST,
            EnumSet.of(AccountsReportParam.MONTH), allowanceOptional(),
            cardNo(), employeeName(), department(), section(), designation(),
            amount("amount", "Seniority Allowance")),

    // Allowances calculation sheet: one column per active EARNING salary
    // component, in catalog display order — the allowance source inserts
    // them before Total at run time (see AllowanceReportSource#columns), so
    // a component HR adds shows up without a code change. Total = sum of
    // the component columns; only employees with a total above zero. The
    // legacy "Management" sheet is this report with Grade Type = MANAGEMENT.

    F7("Allowances Calculation", AccountsReportGroup.ALLOWANCES, AccountsReportOutput.LIST,
            EnumSet.of(AccountsReportParam.MONTH),
            with(with(allowanceOptional(), AccountsReportParam.CARD_LIST), AccountsReportParam.GRADE_TYPE),
            cardNo(), employeeName(), department(), section(), designation(), grade(), dateOfJoining(),
            amount(AccountsReportColumnDto.TOTAL, "Total"), signature());

    private final String title;
    private final AccountsReportGroup group;
    private final AccountsReportOutput output;
    private final Set<AccountsReportParam> required;
    private final Set<AccountsReportParam> optional;
    private final List<AccountsReportColumnDto> columns;
    private final String groupBy;

    AccountsReport(String title, AccountsReportGroup group, AccountsReportOutput output,
                   Set<AccountsReportParam> required, Set<AccountsReportParam> optional,
                   AccountsReportColumnDto... columns) {
        this(title, group, output, (String) null, required, optional, columns);
    }

    /** {@code groupBy}: column key a GROUPED_LIST subtotals on. */
    AccountsReport(String title, AccountsReportGroup group, AccountsReportOutput output, String groupBy,
                   Set<AccountsReportParam> required, Set<AccountsReportParam> optional,
                   AccountsReportColumnDto... columns) {
        this.title = title;
        this.group = group;
        this.output = output;
        this.groupBy = groupBy;
        this.required = Collections.unmodifiableSet(required);
        this.optional = Collections.unmodifiableSet(optional);
        this.columns = List.of(columns);
    }

    /** Column key a GROUPED_LIST subtotals on; null for other outputs. */
    public String groupBy() {
        return groupBy;
    }

    /** Accepts the legacy id ("F-1") and, for allowances, the constant name ("F1"). */
    public static Optional<AccountsReport> byId(String id) {
        return Arrays.stream(values())
                .filter(r -> r.id().equalsIgnoreCase(id) || r.name().equalsIgnoreCase(id))
                .findFirst();
    }

    /** Legacy report id — the constant name, except allowances which are "F-1" … "F-6". */
    public String id() {
        return group == AccountsReportGroup.ALLOWANCES ? "F-" + name().substring(1) : name();
    }

    public String title() {
        return title;
    }

    public AccountsReportGroup group() {
        return group;
    }

    public AccountsReportOutput output() {
        return output;
    }

    public Set<AccountsReportParam> required() {
        return required;
    }

    public Set<AccountsReportParam> optional() {
        return optional;
    }

    /** True when the document endpoint prints a PDF: slips, letters, the ledger and the F-7 sheet. */
    public boolean hasDocument() {
        return switch (output) {
            case SLIP, LETTER, LEDGER -> true;
            default -> this == F7;
        };
    }

    public boolean accepts(AccountsReportParam p) {
        return required.contains(p) || optional.contains(p);
    }

    public List<AccountsReportColumnDto> columns() {
        return columns;
    }

    public Optional<AccountsReportColumnDto> column(String key) {
        return columns.stream().filter(c -> c.key().equals(key)).findFirst();
    }

    /** Permanent / temporary advance reports: Emp Status is mandatory (legacy F15 R) plus the report's own period. */
    private static Set<AccountsReportParam> advanceRequired(AccountsReportParam... extra) {
        EnumSet<AccountsReportParam> s = EnumSet.of(AccountsReportParam.EMP_STATUS);
        s.addAll(List.of(extra));
        return s;
    }

    /** Card range / specific cards / department / section / designation, plus any extras. */
    private static Set<AccountsReportParam> org(AccountsReportParam... extra) {
        EnumSet<AccountsReportParam> s = EnumSet.of(AccountsReportParam.CARD_RANGE, AccountsReportParam.CARD_LIST,
                AccountsReportParam.DEPARTMENT, AccountsReportParam.SECTION, AccountsReportParam.DESIGNATION);
        s.addAll(List.of(extra));
        return s;
    }

    private static Set<AccountsReportParam> with(Set<AccountsReportParam> base, AccountsReportParam extra) {
        EnumSet<AccountsReportParam> s = EnumSet.copyOf(base);
        s.add(extra);
        return s;
    }

    /** Bank letters: payment mode is forced to bank transfer and the bank is required, so neither is optional. */
    private static Set<AccountsReportParam> letterOptional(Set<AccountsReportParam> base) {
        EnumSet<AccountsReportParam> s = EnumSet.copyOf(base);
        s.remove(AccountsReportParam.PAYMENT_MODE);
        s.remove(AccountsReportParam.BANK);
        return s;
    }

    /** Legacy A-filters: fiscal year and salary month are mandatory. */
    private static Set<AccountsReportParam> salaryRequired() {
        return EnumSet.of(AccountsReportParam.FISCAL_YEAR, AccountsReportParam.MONTH);
    }

    /** Legacy A-filters minus branch (no source): payment mode, employee type, cards, bank, org, status. */
    private static Set<AccountsReportParam> salaryOptional() {
        return EnumSet.of(AccountsReportParam.PAYMENT_MODE, AccountsReportParam.EMPLOYEE_TYPE,
                AccountsReportParam.CARD_RANGE, AccountsReportParam.CARD_LIST, AccountsReportParam.BANK,
                AccountsReportParam.DEPARTMENT, AccountsReportParam.SECTION, AccountsReportParam.DESIGNATION,
                AccountsReportParam.EMP_STATUS);
    }

    /** Legacy E-filters: fiscal year and bonus no are mandatory. */
    private static Set<AccountsReportParam> bonusRequired() {
        return EnumSet.of(AccountsReportParam.FISCAL_YEAR, AccountsReportParam.BONUS_NO);
    }

    /** Legacy E-filters minus branch: payment mode, cards, bank, org, status. */
    private static Set<AccountsReportParam> bonusOptional() {
        return EnumSet.of(AccountsReportParam.PAYMENT_MODE, AccountsReportParam.CARD_RANGE,
                AccountsReportParam.CARD_LIST, AccountsReportParam.BANK, AccountsReportParam.DEPARTMENT,
                AccountsReportParam.SECTION, AccountsReportParam.DESIGNATION, AccountsReportParam.EMP_STATUS);
    }

    /** Legacy F-filters minus branch: card range, department, section, designation, status. */
    private static Set<AccountsReportParam> allowanceOptional() {
        return EnumSet.of(AccountsReportParam.CARD_RANGE, AccountsReportParam.DEPARTMENT,
                AccountsReportParam.SECTION, AccountsReportParam.DESIGNATION, AccountsReportParam.EMP_STATUS);
    }

    /** Legacy B-filters: card range, department, section, designation — no specific-cards picker. */
    private static Set<AccountsReportParam> orgWithoutCardList() {
        return EnumSet.of(AccountsReportParam.CARD_RANGE, AccountsReportParam.DEPARTMENT,
                AccountsReportParam.SECTION, AccountsReportParam.DESIGNATION);
    }
}
