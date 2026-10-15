package com.forward.hrms.payroll.service;

import com.forward.hrms.commons.cache.CatalogCache;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.core.error.ValidationError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.employee.dto.response.DepartmentDto;
import com.forward.hrms.employee.enums.EmploymentStatus;
import com.forward.hrms.payroll.dto.request.AccountsReportFilter;
import com.forward.hrms.payroll.dto.response.AccountsReportDefinitionDto;
import com.forward.hrms.payroll.dto.response.AccountsReportResultDto;
import com.forward.hrms.payroll.entity.AnnualLeaveTransaction;
import com.forward.hrms.payroll.entity.Bank;
import com.forward.hrms.payroll.entity.EmployeeBankDetails;
import com.forward.hrms.payroll.entity.EmployeePayrollSettings;
import com.forward.hrms.payroll.entity.EmployeeSalary;
import com.forward.hrms.payroll.entity.Payslip;
import com.forward.hrms.payroll.entity.PayslipLine;
import com.forward.hrms.payroll.entity.ShortTermAdvance;
import com.forward.hrms.payroll.enums.ComponentType;
import com.forward.hrms.payroll.enums.EmployeeStatusFilter;
import com.forward.hrms.payroll.enums.EmployeeTypeFilter;
import com.forward.hrms.payroll.enums.PaymentMode;
import com.forward.hrms.payroll.enums.PaymentModeFilter;
import com.forward.hrms.payroll.enums.ReportExportFormat;
import com.forward.hrms.payroll.enums.ShortTermAdvanceStatus;
import com.forward.hrms.payroll.repository.BankRepository;
import com.forward.hrms.payroll.repository.EmployeeBankDetailsRepository;
import com.forward.hrms.payroll.repository.EmployeePayrollSettingsRepository;
import com.forward.hrms.payroll.repository.PayslipLineRepository;
import com.forward.hrms.payroll.repository.PermanentAdvanceDeductionRepository;
import com.forward.hrms.payroll.repository.PermanentAdvanceRepository;
import com.forward.hrms.payroll.repository.SalaryReportRepository;
import com.forward.hrms.payroll.repository.ShortTermAdvanceRepository;
import com.forward.hrms.payroll.repository.projection.EmployeeAppliedAdvanceProjection;

import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Salary accounts reports (A2, A4, A5, A7, A9) end to end through
 * {@link AccountsReportService} with mocked payroll repositories.
 *
 * <p>Fixture, August 2026, FY 2026-27:
 * <ul>
 *   <li>card 0101 — active, Production, cash; engine payslip: due 27,000 of a
 *       30,000 basic, meal 2,000, travel 2,000, OT 800, canteen 500, rent 1,000,
 *       EOBI 370, net 29,930; open permanent advance 10,000 (display only).</li>
 *   <li>card 20 — active, Admin (non-production), bank transfer at HBL;
 *       legacy-imported payslip: due 50,000, special 5,000, tax 1,200, EOBI 370,
 *       deductions 1,000 holding a 1,000 temporary advance, net 52,430.</li>
 *   <li>card 5 — terminated, Production, bank transfer at MCB (IBAN only);
 *       engine payslip: due and net 20,000.</li>
 * </ul>
 */
class SalaryAccountsReportsTest {

    private final SalaryReportRepository repo = mock(SalaryReportRepository.class);
    private final PayslipLineRepository lineRepo = mock(PayslipLineRepository.class);
    private final EmployeePayrollSettingsRepository settingsRepo = mock(EmployeePayrollSettingsRepository.class);
    private final EmployeeBankDetailsRepository bankDetailsRepo = mock(EmployeeBankDetailsRepository.class);
    private final BankRepository bankRepo = mock(BankRepository.class);
    private final ShortTermAdvanceRepository shortTermRepo = mock(ShortTermAdvanceRepository.class);
    private final PermanentAdvanceRepository permanentRepo = mock(PermanentAdvanceRepository.class);
    private final PermanentAdvanceDeductionRepository deductionRepo = mock(PermanentAdvanceDeductionRepository.class);
    private final AccountsReportEmployees employees = mock(AccountsReportEmployees.class);
    private final CatalogCache catalog = mock(CatalogCache.class);
    private final AuditEmitter audit = mock(AuditEmitter.class);

    private AccountsReportService service;

    @BeforeEach
    void setUp() {
        SalaryReportSource source = new SalaryReportSource(repo, lineRepo, settingsRepo, bankDetailsRepo, bankRepo,
                shortTermRepo, permanentRepo, deductionRepo);
        service = new AccountsReportService(List.of(source), employees, catalog, audit);

        Payslip p1 = payslip(1, 101, "system", "27000", "31800", "1500", "0", "370", "29930");
        p1.setPaidDays(new BigDecimal("27"));
        p1.setOtHours(new BigDecimal("4"));
        p1.setOtAmount(new BigDecimal("800"));
        Payslip p2 = payslip(2, 102, "legacy-migration", "50000", "55000", "1000", "1200", "370", "52430");
        Payslip p3 = payslip(3, 103, "system", "20000", "20000", "0", "0", "0", "20000");
        when(repo.findComputedPayslips(2026, 8)).thenReturn(List.of(p1, p2, p3));
        when(repo.findComputedPayslips(2026, 7)).thenReturn(List.of());

        when(lineRepo.findAllByPayslipIdInOrderByPayslipIdAscDisplayOrderAsc(anyCollection())).thenReturn(List.of(
                line(1, "BASIC", ComponentType.EARNING, "27000"),
                line(1, "MEAL", ComponentType.EARNING, "2000"),
                line(1, "TRAVEL", ComponentType.EARNING, "2000"),
                line(1, "OT", ComponentType.EARNING, "800"),
                line(1, "CANTEEN", ComponentType.DEDUCTION, "500"),
                line(1, "RENT", ComponentType.DEDUCTION, "1000"),
                line(2, "BASIC", ComponentType.EARNING, "50000"),
                line(2, "SPECIAL", ComponentType.EARNING, "5000"),
                line(2, "INCOME_TAX", ComponentType.DEDUCTION, "1200"),
                line(3, "BASIC", ComponentType.EARNING, "20000")));

        when(repo.findEffectiveOn(anyCollection(), any())).thenReturn(List.of(
                salary(101, "30000"), salary(102, "50000"), salary(103, "20000")));

        when(settingsRepo.findAllByEmployeeIdIn(anyCollection())).thenReturn(List.of(
                settings(101, PaymentMode.CASH), settings(102, PaymentMode.BANK_TRANSFER),
                settings(103, PaymentMode.BANK_TRANSFER)));
        when(bankDetailsRepo.findByEmployeeIdIn(anyCollection())).thenReturn(List.of(
                bankDetails(102, 7, "HBL-20", "PK00HBL20"), bankDetails(103, 8, null, "PK00MCB5")));
        Bank hbl = bank(7, "HBL");
        when(bankRepo.findAllByOrderByCodeAsc()).thenReturn(List.of(hbl, bank(8, "MCB")));
        when(bankRepo.findById(7L)).thenReturn(Optional.of(hbl));

        ShortTermAdvance sta = new ShortTermAdvance();
        sta.setEmployeeId(102L);
        sta.setAmount(new BigDecimal("1000"));
        sta.setStatus(ShortTermAdvanceStatus.APPROVED);
        when(shortTermRepo.findAppliedForEmployeesInMonth(anyCollection(), anyInt(), anyInt())).thenReturn(List.of(sta));
        when(permanentRepo.appliedTotalsByEmployeeIds(anyCollection(), any()))
                .thenReturn(List.of(applied(101, "10000")));
        when(deductionRepo.deductedInRunByEmployeeIds(anyCollection(), any())).thenReturn(List.of());

        when(repo.findAnnualLeaveBetween(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))).thenReturn(List.of(
                annualLeave(101, 10, "11538.46", "2000", "9538.46"),
                annualLeave(102, 5, "9615.38", "0", "9615.38")));

        Map<Long, AccountsReportEmployee> emps = new HashMap<>();
        emps.put(101L, employee(101, "0101", "Ali", EmploymentStatus.ACTIVE, 1L, "Production", true));
        emps.put(102L, employee(102, "20", "Bilal", EmploymentStatus.ACTIVE, 2L, "Admin", false));
        emps.put(103L, employee(103, "5", "Camran", EmploymentStatus.TERMINATED, 1L, "Production", true));
        when(employees.resolve(anyCollection())).thenReturn(emps);

        when(catalog.snapshot()).thenReturn(new CatalogCache.CatalogIndex(
                Map.of(1L, new DepartmentDto(1, "PROD", "Production", null, true, true)),
                Map.of(), Map.of(), Map.of(), Map.of()));
    }

    // ---------- A2 register ----------

    @Test
    void register_figuresComeFromThePayslipSnapshot() {
        AccountsReportResultDto a2 = run("A2", salaryFilter());
        assertThat(a2.rows()).extracting(r -> r.get("cardNo")).containsExactly("5", "20", "0101");

        Map<String, Object> ali = a2.rows().get(2);
        assertThat(ali).containsEntry("basicSalary", new BigDecimal("30000"))
                .containsEntry("dueSalary", new BigDecimal("27000"))
                .containsEntry("payableDays", new BigDecimal("27"))
                .containsEntry("otAmount", new BigDecimal("800"))
                .containsEntry("otHours", new BigDecimal("4"))
                .containsEntry("meal", new BigDecimal("2000"))
                .containsEntry("travel", new BigDecimal("2000"))
                .containsEntry("special", BigDecimal.ZERO)
                .containsEntry("canteen", new BigDecimal("500"))
                .containsEntry("rent", new BigDecimal("1000"))
                .containsEntry("totalPayables", new BigDecimal("31800"))
                .containsEntry("totalDeductions", new BigDecimal("1870"))
                .containsEntry("netSalary", new BigDecimal("29930"));
        // Engine payslip: the open advance is shown, nothing is deducted.
        assertThat(ali).containsEntry("permanentAdvance", new BigDecimal("10000"))
                .containsEntry("permanentAdvanceDeduction", BigDecimal.ZERO)
                .containsEntry("temporaryAdvance", BigDecimal.ZERO);

        // Legacy payslip: the temporary advance sits inside its persisted deductions.
        Map<String, Object> bilal = a2.rows().get(1);
        assertThat(bilal).containsEntry("temporaryAdvance", new BigDecimal("1000"))
                .containsEntry("tax", new BigDecimal("1200"))
                .containsEntry("totalDeductions", new BigDecimal("2570"))
                .containsEntry("netSalary", new BigDecimal("52430"));

        assertThat(a2.totals()).containsEntry("netSalary", new BigDecimal("102360"))
                .containsEntry("totalPayables", new BigDecimal("106800"));
        assertThat(a2.totalEmployees()).isEqualTo(3);
    }

    @Test
    void register_fiscalYearMustMatch() {
        AccountsReportFilter other = salaryFilter(x -> x.fiscalYearCode = "2025-26");
        assertThat(run("A2", other).rows()).isEmpty();
        assertThat(run("A2", salaryFilter(x -> x.month = YearMonth.of(2026, 7))).rows()).isEmpty();
    }

    // ---------- filters ----------

    @Test
    void paymentMode_cashTransferAll() {
        assertThat(cards("A2", salaryFilter(x -> x.paymentMode = PaymentModeFilter.CASH))).containsExactly("0101");
        assertThat(cards("A2", salaryFilter(x -> x.paymentMode = PaymentModeFilter.BANK_TRANSFER)))
                .containsExactly("5", "20");
        assertThat(cards("A2", salaryFilter(x -> x.paymentMode = PaymentModeFilter.ALL))).hasSize(3);
    }

    @Test
    void employeeType_productionFromDepartment() {
        assertThat(cards("A2", salaryFilter(x -> x.employeeType = EmployeeTypeFilter.PRODUCTION)))
                .containsExactly("5", "0101");
        assertThat(cards("A2", salaryFilter(x -> x.employeeType = EmployeeTypeFilter.NON_PRODUCTION)))
                .containsExactly("20");
    }

    @Test
    void bankFilter_andCaptionNamesTheBank() {
        AccountsReportResultDto r = run("A2", salaryFilter(x -> x.bankId = 7L));
        assertThat(r.rows()).extracting(x -> x.get("cardNo")).containsExactly("20");
        assertThat(r.appliedFilters()).contains("Bank: HBL").contains("Financial Period: 2026-27")
                .contains("Month: August 2026");
    }

    @Test
    void employeeStatus_activeAndAll() {
        assertThat(cards("A2", salaryFilter(x -> x.status = EmployeeStatusFilter.ACTIVE))).containsExactly("20", "0101");
        assertThat(cards("A2", salaryFilter(x -> x.status = EmployeeStatusFilter.NON_ACTIVE))).containsExactly("5");
        assertThat(cards("A2", salaryFilter())).hasSize(3);
    }

    @Test
    void departmentCardRangeAndLeadingZeroCardList_combine() {
        assertThat(cards("A2", salaryFilter(x -> x.departmentId = 1L, x -> x.cardFrom = 1L, x -> x.cardTo = 200L)))
                .containsExactly("5", "0101");
        assertThat(cards("A2", salaryFilter(x -> x.cardList = List.of("101")))).containsExactly("0101");
    }

    // ---------- A4 / A5 summaries ----------

    @Test
    void departmentSummary_groupsAndCounts() {
        AccountsReportResultDto a4 = run("A4", salaryFilter());
        assertThat(a4.rows()).extracting(r -> r.get("srNo"), r -> r.get("department"), r -> r.get("employeeCount"),
                        r -> r.get("dueSalary"), r -> r.get("netSalary"))
                .containsExactly(
                        tuple(1, "Admin", 1, new BigDecimal("50000"), new BigDecimal("52430")),
                        tuple(2, "Production", 2, new BigDecimal("47000"), new BigDecimal("49930")));
        assertThat(a4.totals()).containsEntry("netSalary", new BigDecimal("102360"));
        assertThat(a4.totalEmployees()).isEqualTo(3);
        // Status filter still applies before grouping.
        AccountsReportResultDto active = run("A4", salaryFilter(x -> x.status = EmployeeStatusFilter.ACTIVE));
        assertThat(active.rows()).extracting(r -> r.get("employeeCount")).containsExactly(1, 1);
    }

    @Test
    void countByDepartment_splitsByPaymentMode() {
        AccountsReportResultDto a5 = run("A5", monthOnly(PaymentModeFilter.ALL));
        assertThat(a5.rows()).extracting(r -> r.get("department"), r -> r.get("paymentMode"),
                        r -> r.get("employeeCount"), r -> r.get("netSalary"), r -> r.get("salaryMonth"))
                .containsExactly(
                        tuple("Admin", "Bank Transfer", 1, new BigDecimal("52430"), YearMonth.of(2026, 8)),
                        tuple("Production", "Bank Transfer", 1, new BigDecimal("20000"), YearMonth.of(2026, 8)),
                        tuple("Production", "Cash", 1, new BigDecimal("29930"), YearMonth.of(2026, 8)));
        assertThat(run("A5", monthOnly(PaymentModeFilter.CASH)).rows()).singleElement()
                .satisfies(r -> assertThat(r.get("department")).isEqualTo("Production"));
    }

    // ---------- A7 bank list ----------

    @Test
    void bankReceivingList_onlyTransferEmployees_groupedByBank() throws Exception {
        AccountsReportFilter f = salaryFilter();
        AccountsReportResultDto a7 = run("A7", f);
        assertThat(a7.rows()).extracting(r -> r.get("cardNo"), r -> r.get("bankName"), r -> r.get("accountNo"))
                .containsExactly(tuple("20", "HBL", "HBL-20"), tuple("5", "MCB", "PK00MCB5"));
        assertThat(a7.rows().getFirst()).containsEntry("fatherName", "Father of Bilal");
        assertThat(a7.groups()).extracting(g -> g.get("group"), g -> g.get("employees"))
                .containsExactly(tuple("HBL", 1L), tuple("MCB", 1L));
        assertThat(a7.totals()).containsEntry("netSalary", new BigDecimal("72430"));

        AccountsReportService.Exported xlsx = service.export("A7", f, ReportExportFormat.XLSX);
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx.body()))) {
            XSSFSheet sheet = wb.getSheetAt(0);
            int last = sheet.getLastRowNum();
            int netCol = 8; // Sr, Card, Name, Father, Dept, Designation, Bank, Account, Net
            List<String> firstCells = new ArrayList<>();
            for (int i = last - 4; i <= last; i++) firstCells.add(text(sheet.getRow(i), 0));
            assertThat(firstCells).containsExactly("1", "Subtotal", "2", "Subtotal", "Total");
            assertThat(sheet.getRow(last - 3).getCell(1).getStringCellValue()).isEqualTo("HBL - 1 employee(s)");
            assertThat(sheet.getRow(last - 3).getCell(netCol).getNumericCellValue()).isEqualTo(52430d);
            assertThat(sheet.getRow(last).getCell(netCol).getNumericCellValue()).isEqualTo(72430d);
        }
    }

    // ---------- A9 annual leave ----------

    @Test
    void annualLeaveEncashment_monthAndPaymentFilter() {
        AccountsReportFilter f = new Builder().status(EmployeeStatusFilter.ALL).month(YearMonth.of(2026, 8)).build();
        AccountsReportResultDto a9 = run("A9", f);
        assertThat(a9.rows()).extracting(r -> r.get("cardNo"), r -> r.get("alDaysPaid"), r -> r.get("netAmount"))
                .containsExactly(tuple("20", new BigDecimal("5"), new BigDecimal("9615.38")),
                        tuple("0101", new BigDecimal("10"), new BigDecimal("9538.46")));
        assertThat(a9.totals()).containsEntry("alAmount", new BigDecimal("21153.84"))
                .containsEntry("alDeduction", new BigDecimal("2000"));

        AccountsReportFilter cash = new Builder().status(EmployeeStatusFilter.ALL).month(YearMonth.of(2026, 8))
                .with(x -> x.paymentMode = PaymentModeFilter.CASH).build();
        assertThat(run("A9", cash).rows()).extracting(r -> r.get("cardNo")).containsExactly("0101");
    }

    // ---------- validation ----------

    @Test
    void validation_returns400() {
        assertBadRequest("A2", salaryFilter(x -> x.fiscalYearCode = null), "fiscalYearCode");
        assertBadRequest("A2", salaryFilter(x -> x.month = null), "month");
        assertBadRequest("A5", monthOnly(null), "paymentMode");
        assertBadRequest("A7", salaryFilter(x -> x.cardFrom = 50L, x -> x.cardTo = 1L), "cardFrom");
        assertBadRequest("A6", salaryFilter(), "bankId");
        assertThatThrownBy(() -> run("A3", salaryFilter())).isInstanceOf(NotFoundError.class);
    }

    private void assertBadRequest(String id, AccountsReportFilter f, String field) {
        assertThatThrownBy(() -> run(id, f))
                .isInstanceOf(ValidationError.class)
                .satisfies(e -> {
                    ValidationError v = (ValidationError) e;
                    assertThat(v.status()).isEqualTo(400);
                    assertThat(v.violations()).extracting(x -> x.field()).contains(field);
                });
    }

    // ---------- registry ----------

    @Test
    void registry_listsSalaryReports() {
        assertThat(service.registry()).extracting(AccountsReportDefinitionDto::id)
                .containsExactly("A1", "A2", "A4", "A5", "A6", "A7", "A9");
        AccountsReportDefinitionDto a5 = service.registry().get(3);
        assertThat(a5.params()).extracting(p -> p.param().name() + (p.required() ? "*" : ""))
                .containsExactlyInAnyOrder("MONTH*", "PAYMENT_MODE*", "DEPARTMENT");
    }

    // ---------- export totals = screen totals ----------

    @Test
    void excelAndCsvTotals_matchScreen() throws Exception {
        AccountsReportFilter f = salaryFilter();
        AccountsReportResultDto screen = run("A2", f);
        int netCol = 25; // Sr No + 24 report columns before Net Salary

        AccountsReportService.Exported xlsx = service.export("A2", f, ReportExportFormat.XLSX);
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx.body()))) {
            XSSFSheet sheet = wb.getSheetAt(0);
            XSSFRow header = sheet.getRow(5);
            assertThat(header.getCell(netCol).getStringCellValue()).isEqualTo("Net Salary");
            XSSFRow total = sheet.getRow(sheet.getLastRowNum());
            assertThat(total.getCell(0).getStringCellValue()).isEqualTo("Total");
            assertThat(BigDecimal.valueOf(total.getCell(netCol).getNumericCellValue()))
                    .isEqualByComparingTo(screen.totals().get("netSalary"));
            assertThat(sheet.getRow(3).getCell(0).getStringCellValue()).startsWith("Printed ");
        }

        AccountsReportService.Exported csv = service.export("A2", f, ReportExportFormat.CSV);
        List<String> csvLines = new String(csv.body(), StandardCharsets.UTF_8).lines().toList();
        assertThat(csvLines).hasSize(screen.rows().size() + 2);
        String[] totalCells = csvLines.getLast().split(",", -1);
        assertThat(totalCells[0]).isEqualTo("Total");
        assertThat(new BigDecimal(totalCells[netCol])).isEqualByComparingTo(screen.totals().get("netSalary"));
    }

    // ---------- fixture helpers ----------

    private AccountsReportResultDto run(String id, AccountsReportFilter f) {
        return service.data(id, f);
    }

    private List<Object> cards(String id, AccountsReportFilter f) {
        return run(id, f).rows().stream().map(r -> r.get("cardNo")).toList();
    }

    private static String text(XSSFRow row, int col) {
        var cell = row.getCell(col);
        return switch (cell.getCellType()) {
            case NUMERIC -> String.valueOf((int) cell.getNumericCellValue());
            default -> cell.getStringCellValue();
        };
    }

    private static Payslip payslip(long id, long employeeId, String createdBy, String basic, String gross,
                                   String deductions, String tax, String eobi, String net) {
        Payslip p = new Payslip();
        p.setId(id);
        p.setPayrollRunId(900L);
        p.setEmployeeId(employeeId);
        p.setPeriodYear(2026);
        p.setPeriodMonth(8);
        p.setFiscalYearCode("2026-27");
        p.setBasic(new BigDecimal(basic));
        p.setGross(new BigDecimal(gross));
        p.setDeductions(new BigDecimal(deductions));
        p.setTax(new BigDecimal(tax));
        p.setEobi(new BigDecimal(eobi));
        p.setNet(new BigDecimal(net));
        p.setPaidDays(new BigDecimal("31"));
        p.setCreatedBy(createdBy);
        return p;
    }

    private static PayslipLine line(long payslipId, String code, ComponentType type, String amount) {
        PayslipLine l = new PayslipLine();
        l.setPayslipId(payslipId);
        l.setCode(code);
        l.setName(code);
        l.setType(type);
        l.setAmount(new BigDecimal(amount));
        return l;
    }

    private static EmployeeSalary salary(long employeeId, String basic) {
        EmployeeSalary s = new EmployeeSalary();
        s.setEmployeeId(employeeId);
        s.setBasic(new BigDecimal(basic));
        s.setEffectiveFrom(LocalDate.of(2025, 1, 1));
        return s;
    }

    private static EmployeePayrollSettings settings(long employeeId, PaymentMode mode) {
        EmployeePayrollSettings s = new EmployeePayrollSettings();
        s.setEmployeeId(employeeId);
        s.setPaymentMode(mode);
        return s;
    }

    private static EmployeeBankDetails bankDetails(long employeeId, long bankId, String account, String iban) {
        EmployeeBankDetails b = new EmployeeBankDetails();
        b.setEmployeeId(employeeId);
        b.setBankId(bankId);
        b.setAccountNumber(account);
        b.setIban(iban);
        return b;
    }

    private static Bank bank(long id, String name) {
        Bank b = new Bank();
        b.setId(id);
        b.setCode(name);
        b.setName(name);
        return b;
    }

    private static EmployeeAppliedAdvanceProjection applied(long employeeId, String amount) {
        return new EmployeeAppliedAdvanceProjection() {
            @Override
            public Long getEmployeeId() {
                return employeeId;
            }

            @Override
            public BigDecimal getAppliedAmount() {
                return new BigDecimal(amount);
            }
        };
    }

    private static AnnualLeaveTransaction annualLeave(long employeeId, long days, String gross, String deduction,
                                                      String net) {
        AnnualLeaveTransaction t = new AnnualLeaveTransaction();
        t.setEmployeeId(employeeId);
        t.setCalculationMonth(LocalDate.of(2026, 8, 1));
        t.setPayableLeaveDays(days);
        t.setGrossAnnualLeaveAmount(new BigDecimal(gross));
        t.setDeductionAsLongTermAdvance(new BigDecimal(deduction));
        t.setNetAmount(new BigDecimal(net));
        return t;
    }

    private static AccountsReportEmployee employee(long id, String card, String name, EmploymentStatus status,
                                                   Long departmentId, String department, boolean production) {
        return new AccountsReportEmployee(id, card, name, "Father of " + name, null, LocalDate.of(2020, 1, 1),
                status, "PERMANENT", departmentId, department, null, null, null, null, null, null, production);
    }

    @SafeVarargs
    private static AccountsReportFilter salaryFilter(Consumer<Builder>... edits) {
        Builder b = new Builder().status(EmployeeStatusFilter.ALL).month(YearMonth.of(2026, 8));
        b.fiscalYearCode = "2026-27";
        for (var e : edits) e.accept(b);
        return b.build();
    }

    private static AccountsReportFilter monthOnly(PaymentModeFilter mode) {
        return new Builder().month(YearMonth.of(2026, 8)).with(x -> x.paymentMode = mode).build();
    }

    /** Mutable stand-in for the filter record so each test names only what it sets. */
    private static final class Builder {
        String fiscalYearCode;
        YearMonth month;
        Long cardFrom, cardTo, departmentId, bankId;
        List<String> cardList;
        EmployeeStatusFilter status;
        PaymentModeFilter paymentMode;
        EmployeeTypeFilter employeeType;

        Builder status(EmployeeStatusFilter s) {
            status = s;
            return this;
        }

        Builder month(YearMonth m) {
            month = m;
            return this;
        }

        Builder with(Consumer<Builder> edit) {
            edit.accept(this);
            return this;
        }

        AccountsReportFilter build() {
            return new AccountsReportFilter(fiscalYearCode, month, null, null, null, cardFrom, cardTo, cardList,
                    departmentId, null, null, status, null, null, null, null, null, null, null,
                    paymentMode, employeeType, bankId);
        }
    }
}
