package com.forward.hrms.payroll.service;

import com.forward.hrms.commons.cache.CatalogCache;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.core.error.ValidationError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.employee.dto.response.DepartmentDto;
import com.forward.hrms.employee.enums.EmploymentStatus;
import com.forward.hrms.payroll.dto.request.AccountsReportFilter;
import com.forward.hrms.payroll.dto.response.AccountsReportColumnDto;
import com.forward.hrms.payroll.dto.response.AccountsReportDefinitionDto;
import com.forward.hrms.payroll.dto.response.AccountsReportResultDto;
import com.forward.hrms.payroll.entity.Bank;
import com.forward.hrms.payroll.entity.BonusTransaction;
import com.forward.hrms.payroll.entity.EmployeeBankDetails;
import com.forward.hrms.payroll.enums.BonusTransactionPaymentMode;
import com.forward.hrms.payroll.enums.EmployeeStatusFilter;
import com.forward.hrms.payroll.enums.PaymentModeFilter;
import com.forward.hrms.payroll.enums.ReportExportFormat;
import com.forward.hrms.payroll.repository.BankRepository;
import com.forward.hrms.payroll.repository.BonusReportRepository;
import com.forward.hrms.payroll.repository.EmployeeBankDetailsRepository;

import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bonus accounts reports (E2, E3, E7) end to end through
 * {@link AccountsReportService} with a mocked bonus ledger.
 *
 * <p>Fixture, FY 2026-27 bonus 1:
 * <ul>
 *   <li>card 0101 — active, Production, cash: basic 30,000, due 29,750, rounded
 *       up by RFD 250 to 30,000.</li>
 *   <li>card 20 — active, Admin, bank transfer at HBL: basic 50,000, due 27,397,
 *       permanent advance 2,000 taken off, net 25,397.</li>
 *   <li>card 5 — terminated, Production, cash: basic 20,000, due 5,479, RFD 21,
 *       net 5,500.</li>
 * </ul>
 */
class BonusAccountsReportsTest {

    private final BonusReportRepository repo = mock(BonusReportRepository.class);
    private final EmployeeBankDetailsRepository bankDetailsRepo = mock(EmployeeBankDetailsRepository.class);
    private final BankRepository bankRepo = mock(BankRepository.class);
    private final AccountsReportEmployees employees = mock(AccountsReportEmployees.class);
    private final CatalogCache catalog = mock(CatalogCache.class);
    private final AuditEmitter audit = mock(AuditEmitter.class);

    private AccountsReportService service;

    @BeforeEach
    void setUp() {
        service = new AccountsReportService(List.of(new BonusReportSource(repo, bankDetailsRepo, bankRepo)),
                employees, catalog, audit);

        when(repo.findComputed("2026-27", 1)).thenReturn(List.of(
                bonus(101, BonusTransactionPaymentMode.CASH, "30000", 365, "29750", "0", "250", "30000"),
                bonus(102, BonusTransactionPaymentMode.BANK_TRANSFER, "50000", 200, "27397", "2000", "0", "25397"),
                bonus(103, BonusTransactionPaymentMode.CASH, "20000", 100, "5479", "0", "21", "5500")));
        when(repo.findComputed("2026-27", 2)).thenReturn(List.of());

        EmployeeBankDetails hblAccount = new EmployeeBankDetails();
        hblAccount.setEmployeeId(102L);
        hblAccount.setBankId(7L);
        when(bankDetailsRepo.findByEmployeeIdIn(anyCollection())).thenReturn(List.of(hblAccount));
        Bank hbl = new Bank();
        hbl.setId(7L);
        hbl.setName("HBL");
        when(bankRepo.findById(7L)).thenReturn(Optional.of(hbl));

        Map<Long, AccountsReportEmployee> emps = new HashMap<>();
        emps.put(101L, employee(101, "0101", "Ali", EmploymentStatus.ACTIVE, 1L, "Production"));
        emps.put(102L, employee(102, "20", "Bilal", EmploymentStatus.ACTIVE, 2L, "Admin"));
        emps.put(103L, employee(103, "5", "Camran", EmploymentStatus.TERMINATED, 1L, "Production"));
        when(employees.resolve(anyCollection())).thenReturn(emps);

        when(catalog.snapshot()).thenReturn(new CatalogCache.CatalogIndex(
                Map.of(1L, new DepartmentDto(1, "PROD", "Production", null, true, true)),
                Map.of(), Map.of(), Map.of(), Map.of()));
    }

    // ---------- E2 list ----------

    @Test
    void bonusList_figuresAreTheStoredTransaction() {
        AccountsReportResultDto e2 = run("E2", bonusFilter());
        assertThat(e2.rows()).extracting(r -> r.get("cardNo"), r -> r.get("dueAmount"), r -> r.get("advanceDeduction"),
                        r -> r.get("payableAmount"))
                .containsExactly(
                        tuple("5", new BigDecimal("5479"), BigDecimal.ZERO, new BigDecimal("5500")),
                        tuple("20", new BigDecimal("27397"), new BigDecimal("2000"), new BigDecimal("25397")),
                        tuple("0101", new BigDecimal("29750"), BigDecimal.ZERO, new BigDecimal("30000")));
        assertThat(e2.rows().get(2)).containsEntry("basicSalary", new BigDecimal("30000"))
                .containsEntry("noOfDays", new BigDecimal("365"))
                .containsEntry("dateOfJoining", LocalDate.of(2020, 1, 1))
                .containsEntry("signature", "");
        assertThat(e2.totals()).containsEntry("dueAmount", new BigDecimal("62626"))
                .containsEntry("advanceDeduction", new BigDecimal("2000"))
                .containsEntry("payableAmount", new BigDecimal("60897"));
        assertThat(e2.columns()).extracting(AccountsReportColumnDto::label)
                .contains("Basic Salary").doesNotContain("Gross Salary");
    }

    @Test
    void otherBonusNo_isItsOwnBonus() {
        assertThat(run("E2", bonusFilter(x -> x.bonusNo = 2)).rows()).isEmpty();
    }

    // ---------- filters ----------

    @Test
    void paymentMode_usesTheBonusOwnMode() {
        assertThat(cards("E2", bonusFilter(x -> x.paymentMode = PaymentModeFilter.CASH))).containsExactly("5", "0101");
        assertThat(cards("E2", bonusFilter(x -> x.paymentMode = PaymentModeFilter.BANK_TRANSFER)))
                .containsExactly("20");
        assertThat(cards("E2", bonusFilter(x -> x.paymentMode = PaymentModeFilter.ALL))).hasSize(3);
    }

    @Test
    void bankFilter_andCaption() {
        AccountsReportResultDto r = run("E2", bonusFilter(x -> x.bankId = 7L));
        assertThat(r.rows()).extracting(x -> x.get("cardNo")).containsExactly("20");
        assertThat(r.appliedFilters()).contains("Bank: HBL").contains("Financial Period: 2026-27")
                .contains("Bonus No: 1");
    }

    @Test
    void employeeStatus_activeNonActiveAll() {
        assertThat(cards("E2", bonusFilter(x -> x.status = EmployeeStatusFilter.ACTIVE))).containsExactly("20", "0101");
        assertThat(cards("E2", bonusFilter(x -> x.status = EmployeeStatusFilter.NON_ACTIVE))).containsExactly("5");
        assertThat(cards("E2", bonusFilter())).hasSize(3);
    }

    @Test
    void departmentCardRangeAndLeadingZeroCards_combine() {
        assertThat(cards("E2", bonusFilter(x -> x.departmentId = 1L, x -> x.cardFrom = 1L, x -> x.cardTo = 200L)))
                .containsExactly("5", "0101");
        assertThat(cards("E2", bonusFilter(x -> x.cardList = List.of("101")))).containsExactly("0101");
    }

    // ---------- E3 summary ----------

    @Test
    void departmentSummary_sumsBonusAndRfd() {
        AccountsReportResultDto e3 = run("E3", bonusFilter());
        assertThat(e3.rows()).extracting(r -> r.get("department"), r -> r.get("employeeCount"),
                        r -> r.get("basicSalary"), r -> r.get("dueAmount"), r -> r.get("advanceDeduction"),
                        r -> r.get("rfdAdvanceAdded"), r -> r.get("payableAmount"))
                .containsExactly(
                        tuple("Admin", 1, new BigDecimal("50000"), new BigDecimal("27397"), new BigDecimal("2000"),
                                BigDecimal.ZERO, new BigDecimal("25397")),
                        tuple("Production", 2, new BigDecimal("50000"), new BigDecimal("35229"), BigDecimal.ZERO,
                                new BigDecimal("271"), new BigDecimal("35500")));
        assertThat(e3.totals()).containsEntry("payableAmount", new BigDecimal("60897"))
                .containsEntry("rfdAdvanceAdded", new BigDecimal("271"));
        assertThat(e3.totalEmployees()).isEqualTo(3);
    }

    // ---------- E7 ----------

    @Test
    void advanceDeductedFromBonus_onlyRowsWithADeduction() {
        AccountsReportResultDto e7 = run("E7", bonusFilter());
        assertThat(e7.rows()).singleElement().satisfies(r -> {
            assertThat(r.get("cardNo")).isEqualTo("20");
            assertThat(r.get("amountDeducted")).isEqualTo(new BigDecimal("2000"));
            assertThat(r.get("financialPeriod")).isEqualTo("2026-27");
            assertThat(r.get("bonusNo")).isEqualTo(1);
        });
        assertThat(e7.totals()).containsEntry("amountDeducted", new BigDecimal("2000"));
    }

    // ---------- validation / registry ----------

    @Test
    void validation_returns400() {
        assertBadRequest("E2", bonusFilter(x -> x.bonusNo = null), "bonusNo");
        assertBadRequest("E3", bonusFilter(x -> x.fiscalYearCode = null), "fiscalYearCode");
        assertBadRequest("E7", bonusFilter(x -> x.bonusNo = 0), "bonusNo");
        assertBadRequest("E2", bonusFilter(x -> x.cardFrom = 90L, x -> x.cardTo = 10L), "cardFrom");
        assertBadRequest("E5", bonusFilter(), "bankId");
        assertThatThrownBy(() -> run("E4", bonusFilter())).isInstanceOf(NotFoundError.class);
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

    @Test
    void registry_listsBonusReports() {
        assertThat(service.registry()).extracting(AccountsReportDefinitionDto::id)
                .containsExactly("E1", "E2", "E3", "E5", "E7");
        assertThat(service.registry().get(1).params())
                .extracting(p -> p.param().name() + (p.required() ? "*" : ""))
                .contains("FISCAL_YEAR*", "BONUS_NO*", "PAYMENT_MODE", "BANK")
                .doesNotContain("EMPLOYEE_TYPE", "MONTH");
    }

    // ---------- export totals = screen totals ----------

    @Test
    void excelAndCsvTotals_matchScreen() throws Exception {
        AccountsReportFilter f = bonusFilter();
        AccountsReportResultDto screen = run("E2", f);
        int payableCol = 10; // Sr, Card, Name, DOJ, Dept, Designation, Basic, Days, Due, P.Adv, Payable

        AccountsReportService.Exported xlsx = service.export("E2", f, ReportExportFormat.XLSX);
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx.body()))) {
            XSSFSheet sheet = wb.getSheetAt(0);
            assertThat(sheet.getRow(5).getCell(payableCol).getStringCellValue()).isEqualTo("Rounded Payable Amount");
            XSSFRow total = sheet.getRow(sheet.getLastRowNum());
            assertThat(total.getCell(0).getStringCellValue()).isEqualTo("Total");
            assertThat(BigDecimal.valueOf(total.getCell(payableCol).getNumericCellValue()))
                    .isEqualByComparingTo(screen.totals().get("payableAmount"));
            assertThat(sheet.getRow(3).getCell(0).getStringCellValue()).startsWith("Printed ");
        }

        AccountsReportService.Exported csv = service.export("E2", f, ReportExportFormat.CSV);
        List<String> csvLines = new String(csv.body(), StandardCharsets.UTF_8).lines().toList();
        assertThat(csvLines).hasSize(screen.rows().size() + 2);
        String[] totalCells = csvLines.getLast().split(",", -1);
        assertThat(totalCells[0]).isEqualTo("Total");
        assertThat(new BigDecimal(totalCells[payableCol])).isEqualByComparingTo(screen.totals().get("payableAmount"));
        assertThat(csvLines.get(1)).contains("01-01-2020");
    }

    // ---------- fixture helpers ----------

    private AccountsReportResultDto run(String id, AccountsReportFilter f) {
        return service.data(id, f);
    }

    private List<Object> cards(String id, AccountsReportFilter f) {
        return run(id, f).rows().stream().map(r -> r.get("cardNo")).toList();
    }

    private static BonusTransaction bonus(long employeeId, BonusTransactionPaymentMode mode, String basic, long days,
                                          String due, String deduction, String rfd, String net) {
        BonusTransaction t = new BonusTransaction();
        t.setEmployeeId(employeeId);
        t.setFiscalYearCode("2026-27");
        t.setBonusNo(1);
        t.setBonusTransactionPaymentMode(mode);
        t.setBasicSalary(new BigDecimal(basic));
        t.setNoOfDays(days);
        t.setPercentageSalaryAmount(new BigDecimal(due));
        t.setDeductionAsLongTermAdvance(new BigDecimal(deduction));
        t.setRfdAdvanceAdded(new BigDecimal(rfd));
        t.setNetAmount(new BigDecimal(net));
        return t;
    }

    private static AccountsReportEmployee employee(long id, String card, String name, EmploymentStatus status,
                                                   Long departmentId, String department) {
        return new AccountsReportEmployee(id, card, name, "Father of " + name, null, LocalDate.of(2020, 1, 1),
                status, "PERMANENT", departmentId, department, null, null, null, null, null, null);
    }

    @SafeVarargs
    private static AccountsReportFilter bonusFilter(Consumer<Builder>... edits) {
        Builder b = new Builder();
        b.fiscalYearCode = "2026-27";
        b.bonusNo = 1;
        b.status = EmployeeStatusFilter.ALL;
        for (var e : edits) e.accept(b);
        return b.build();
    }

    /** Mutable stand-in for the filter record so each test names only what it sets. */
    private static final class Builder {
        String fiscalYearCode;
        Integer bonusNo;
        Long cardFrom, cardTo, departmentId, bankId;
        List<String> cardList;
        EmployeeStatusFilter status;
        PaymentModeFilter paymentMode;

        AccountsReportFilter build() {
            return new AccountsReportFilter(fiscalYearCode, null, null, null, null, cardFrom, cardTo, cardList,
                    departmentId, null, null, status, null, bonusNo, null, null, null, null, null,
                    paymentMode, null, bankId);
        }
    }
}
