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
import com.forward.hrms.payroll.entity.ShortTermAdvance;
import com.forward.hrms.payroll.enums.EmployeeStatusFilter;
import com.forward.hrms.payroll.enums.ReportExportFormat;
import com.forward.hrms.payroll.enums.ShortTermAdvanceStatus;
import com.forward.hrms.payroll.repository.BankRepository;
import com.forward.hrms.payroll.repository.EmployeeBankDetailsRepository;
import com.forward.hrms.payroll.repository.EmployeePayrollSettingsRepository;
import com.forward.hrms.payroll.repository.PermanentAdvanceReportRepository;
import com.forward.hrms.payroll.repository.ShortTermAdvanceReportRepository;

import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Temporary-advance accounts reports (B1, B2, B4, B5) end to end through
 * {@link AccountsReportService}. The mocked repository honours the month
 * window it is asked for, so month / year boundaries are exercised; the
 * batch / line status condition lives in the JPQL itself.
 *
 * <p>Fixture (all paid lines): card 0101 (active, dept 1) 3,000 in Jan-2026
 * and 2,000 in Feb-2026; card 20 (terminated, dept 2) 1,500 in Jan-2026;
 * card 5 (active, dept 1) 1,000 in Dec-2025 and 500 in Jan-2026.
 */
class TemporaryAdvanceAccountsReportsTest {

    private final ShortTermAdvanceReportRepository repo = mock(ShortTermAdvanceReportRepository.class);
    private final AccountsReportEmployees employees = mock(AccountsReportEmployees.class);
    private final CatalogCache catalog = mock(CatalogCache.class);
    private final AuditEmitter audit = mock(AuditEmitter.class);

    private AccountsReportService service;

    private final List<ShortTermAdvance> lines = List.of(
            line(1, 101, 2026, 1, "3000"),
            line(2, 101, 2026, 2, "2000"),
            line(3, 102, 2026, 1, "1500"),
            line(4, 103, 2025, 12, "1000"),
            line(5, 103, 2026, 1, "500"));

    @BeforeEach
    void setUp() {
        service = new AccountsReportService(List.of(temporarySource()), employees, catalog, audit);

        when(repo.findPaidBetween(anyInt(), anyInt())).thenAnswer(inv -> {
            int from = inv.getArgument(0);
            int to = inv.getArgument(1);
            return lines.stream()
                    .filter(s -> {
                        int k = s.getTargetYear() * 12 + s.getTargetMonth();
                        return k >= from && k <= to;
                    }).toList();
        });

        Map<Long, AccountsReportEmployee> emps = new HashMap<>();
        emps.put(101L, employee(101, "0101", "Ali", EmploymentStatus.ACTIVE, 1L));
        emps.put(102L, employee(102, "20", "Bilal", EmploymentStatus.TERMINATED, 2L));
        emps.put(103L, employee(103, "5", "Camran", EmploymentStatus.ACTIVE, 1L));
        when(employees.resolve(anyCollection())).thenReturn(emps);

        when(catalog.snapshot()).thenReturn(new CatalogCache.CatalogIndex(
                Map.of(1L, new DepartmentDto(1, "PROD", "Production", null, true, true)),
                Map.of(), Map.of(), Map.of(), Map.of()));
    }

    // ---------- each report's own condition ----------

    @Test
    void paid_b1_everyLineWhoseMonthFallsInRange() {
        AccountsReportResultDto b1 = run("B1", range(LocalDate.of(2026, 1, 15), LocalDate.of(2026, 2, 10)));
        assertThat(b1.rows()).extracting(r -> r.get("cardNo"), r -> r.get("advanceMonth"), r -> r.get("advanceAmount"))
                .containsExactly(
                        tuple("5", YearMonth.of(2026, 1), new BigDecimal("500")),
                        tuple("20", YearMonth.of(2026, 1), new BigDecimal("1500")),
                        tuple("0101", YearMonth.of(2026, 1), new BigDecimal("3000")),
                        tuple("0101", YearMonth.of(2026, 2), new BigDecimal("2000")));
        assertThat(b1.totals()).containsEntry("advanceAmount", new BigDecimal("7000"));
        assertThat(b1.totalEmployees()).isEqualTo(3);
        assertThat(b1.rows().getFirst().get("employeeStatus")).isEqualTo("Active");
        assertThat(b1.rows().getFirst()).doesNotContainKey("signature");
    }

    @Test
    void receivedAgainstSalary_b2_sumsPerEmployeeForTheMonth() {
        AccountsReportResultDto b2 = run("B2", month(2026, 1, EmployeeStatusFilter.ALL));
        assertThat(b2.rows()).extracting(r -> r.get("cardNo"), r -> r.get("amountRecovered"))
                .containsExactly(tuple("5", new BigDecimal("500")), tuple("20", new BigDecimal("1500")),
                        tuple("0101", new BigDecimal("3000")));
        assertThat(b2.totals()).containsEntry("amountRecovered", new BigDecimal("5000"));
    }

    @Test
    void receivingList_b5_oneRowPerLineWithSignature() {
        AccountsReportResultDto b5 = run("B5", month(2026, 2, EmployeeStatusFilter.ALL));
        assertThat(b5.rows()).singleElement().satisfies(r -> {
            assertThat(r.get("cardNo")).isEqualTo("0101");
            assertThat(r.get("advanceAmount")).isEqualTo(new BigDecimal("2000"));
            assertThat(r).containsEntry("signature", "");
        });
    }

    @Test
    void receivedList_b4_signatureColumnNotStatus() {
        AccountsReportResultDto b4 = run("B4", range(LocalDate.of(2025, 12, 1), LocalDate.of(2025, 12, 31)));
        assertThat(b4.rows()).singleElement().satisfies(r -> {
            assertThat(r.get("cardNo")).isEqualTo("5");
            assertThat(r).containsKey("signature").doesNotContainKey("employeeStatus");
        });
    }

    // ---------- month / date boundaries ----------

    @Test
    void range_anyDayInsideTheMonthCountsAndYearBoundaryWorks() {
        assertThat(run("B1", range(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 1, 31))).rows())
                .extracting(r -> r.get("advanceMonth")).containsOnly(YearMonth.of(2026, 1)).hasSize(3);
        assertThat(run("B1", range(LocalDate.of(2025, 12, 1), LocalDate.of(2026, 1, 1))).rows())
                .extracting(r -> r.get("advanceAmount"))
                .containsExactlyInAnyOrder(new BigDecimal("1000"), new BigDecimal("500"),
                        new BigDecimal("1500"), new BigDecimal("3000"));
        assertThat(run("B5", month(2026, 3, EmployeeStatusFilter.ALL)).rows()).isEmpty();
    }

    // ---------- employee filters ----------

    @Test
    void employeeStatus_activeNonActiveAll() {
        assertThat(run("B5", month(2026, 1, EmployeeStatusFilter.ACTIVE)).rows())
                .extracting(r -> r.get("cardNo")).containsExactly("5", "0101");
        assertThat(run("B5", month(2026, 1, EmployeeStatusFilter.NON_ACTIVE)).rows())
                .extracting(r -> r.get("cardNo")).containsExactly("20");
        assertThat(run("B5", month(2026, 1, EmployeeStatusFilter.ALL)).rows()).hasSize(3);
    }

    @Test
    void departmentAndCardRange_combine() {
        AccountsReportFilter f = filter(x -> x.dateFrom = LocalDate.of(2026, 1, 1),
                x -> x.dateTo = LocalDate.of(2026, 2, 28), x -> x.departmentId = 1L,
                x -> x.cardFrom = 1L, x -> x.cardTo = 50L);
        AccountsReportResultDto r = run("B1", f);
        assertThat(r.rows()).extracting(x -> x.get("cardNo")).containsExactly("5");
        assertThat(r.appliedFilters()).contains("Department: Production").contains("Cards: 1 - 50");
    }

    @Test
    void cardRange_matchesLeadingZeroCards() {
        AccountsReportFilter f = filter(x -> x.month = YearMonth.of(2026, 1), x -> x.cardFrom = 100L,
                x -> x.cardTo = 200L);
        assertThat(run("B2", f).rows()).extracting(x -> x.get("cardNo")).containsExactly("0101");
    }

    @Test
    void search_and_paging_keepTotalsOverFullResult() {
        AccountsReportFilter page2 = filter(x -> x.month = YearMonth.of(2026, 1), x -> x.page = 1, x -> x.size = 2);
        AccountsReportResultDto r = run("B5", page2);
        assertThat(r.rows()).singleElement().satisfies(x -> {
            assertThat(x.get("srNo")).isEqualTo(3);
            assertThat(x.get("cardNo")).isEqualTo("0101");
        });
        assertThat(r.totalElements()).isEqualTo(3);
        assertThat(r.totalPages()).isEqualTo(2);
        assertThat(r.totals()).containsEntry("advanceAmount", new BigDecimal("5000"));

        AccountsReportFilter search = filter(x -> x.month = YearMonth.of(2026, 1), x -> x.search = "bil");
        assertThat(run("B5", search).rows()).extracting(x -> x.get("cardNo")).containsExactly("20");
    }

    // ---------- validation ----------

    @Test
    void validation_returns400() {
        assertBadRequest("B1", filter(x -> x.dateFrom = LocalDate.of(2026, 1, 1)), "dateFrom");
        assertBadRequest("B2", filter(), "month");
        assertBadRequest("B4", filter(x -> x.dateFrom = LocalDate.of(2026, 2, 1),
                x -> x.dateTo = LocalDate.of(2026, 1, 1)), "dateFrom");
        assertBadRequest("B5", new Builder().month(YearMonth.of(2026, 1)).build(), "empStatus");
        assertBadRequest("B2", filter(x -> x.month = YearMonth.of(2026, 1), x -> x.cardFrom = 90L,
                x -> x.cardTo = 10L), "cardFrom");
        assertBadRequest("B6", filter(x -> x.month = YearMonth.of(2026, 1)), "bankId");
        assertThatThrownBy(() -> run("B7", filter())).isInstanceOf(NotFoundError.class);
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
    void registry_listsTemporaryBeforePermanent() {
        AccountsReportService both = new AccountsReportService(List.of(temporarySource(),
                new PermanentAdvanceReportSource(mock(PermanentAdvanceReportRepository.class))),
                employees, catalog, audit);
        assertThat(both.registry()).extracting(AccountsReportDefinitionDto::id)
                .containsExactly("B1", "B2", "B3", "B4", "B5", "B6",
                        "C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10", "C11");
        AccountsReportDefinitionDto b1 = both.registry().getFirst();
        assertThat(b1.params()).extracting(p -> p.param().name())
                .containsExactlyInAnyOrder("DATE_RANGE", "CARD_RANGE", "DEPARTMENT", "SECTION", "DESIGNATION",
                        "EMP_STATUS");
        assertThat(b1.columns()).extracting(AccountsReportColumnDto::key).first().isEqualTo("srNo");
    }

    // ---------- export totals = screen totals ----------

    @Test
    void excelAndCsvTotals_matchScreen() throws Exception {
        AccountsReportFilter f = range(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 28));
        AccountsReportResultDto screen = run("B1", f);
        BigDecimal screenTotal = screen.totals().get("advanceAmount");
        int amountCol = 7; // Sr No, Card, Name, Dept, Section, Designation, Month, Amount, Status

        AccountsReportService.Exported xlsx = service.export("B1", f, ReportExportFormat.XLSX);
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx.body()))) {
            XSSFSheet sheet = wb.getSheetAt(0);
            int last = sheet.getLastRowNum();
            assertThat(sheet.getRow(last).getCell(0).getStringCellValue()).isEqualTo("Total");
            assertThat(sheet.getRow(last).getCell(1).getStringCellValue()).isEqualTo("3 employee(s)");
            assertThat(BigDecimal.valueOf(sheet.getRow(last).getCell(amountCol).getNumericCellValue()))
                    .isEqualByComparingTo(screenTotal);
            assertThat(sheet.getRow(last - 1).getCell(6).getStringCellValue()).isEqualTo("February 2026");
            assertThat(sheet.getRow(3).getCell(0).getStringCellValue()).startsWith("Printed ");
        }

        AccountsReportService.Exported csv = service.export("B1", f, ReportExportFormat.CSV);
        List<String> csvLines = new String(csv.body(), StandardCharsets.UTF_8).lines().toList();
        assertThat(csvLines).hasSize(screen.rows().size() + 2);
        String[] totalCells = csvLines.getLast().split(",", -1);
        assertThat(totalCells[0]).isEqualTo("Total");
        assertThat(new BigDecimal(totalCells[amountCol])).isEqualByComparingTo(screenTotal);
        assertThat(csvLines.get(1)).contains("January 2026");
    }

    // ---------- fixture helpers ----------

    private AccountsReportResultDto run(String id, AccountsReportFilter f) {
        return service.data(id, f);
    }

    private TemporaryAdvanceReportSource temporarySource() {
        return new TemporaryAdvanceReportSource(repo, mock(EmployeePayrollSettingsRepository.class),
                mock(EmployeeBankDetailsRepository.class), mock(BankRepository.class));
    }

    private static ShortTermAdvance line(long id, long employeeId, int year, int month, String amount) {
        ShortTermAdvance s = new ShortTermAdvance();
        s.setId(id);
        s.setBatchId(1L);
        s.setEmployeeId(employeeId);
        s.setTargetYear(year);
        s.setTargetMonth(month);
        s.setAmount(new BigDecimal(amount));
        s.setStatus(ShortTermAdvanceStatus.APPROVED);
        return s;
    }

    private static AccountsReportEmployee employee(long id, String card, String name, EmploymentStatus status,
                                                   Long departmentId) {
        return new AccountsReportEmployee(id, card, name, "Father of " + name, null, LocalDate.of(2020, 1, 1),
                status, "PERMANENT", departmentId, departmentId == 1L ? "Production" : "Admin",
                null, null, null, null, null, null);
    }

    private static AccountsReportFilter month(int year, int month, EmployeeStatusFilter status) {
        return new Builder().status(status).month(YearMonth.of(year, month)).build();
    }

    private static AccountsReportFilter range(LocalDate from, LocalDate to) {
        return filter(x -> x.dateFrom = from, x -> x.dateTo = to);
    }

    @SafeVarargs
    private static AccountsReportFilter filter(Consumer<Builder>... edits) {
        Builder b = new Builder().status(EmployeeStatusFilter.ALL);
        for (var e : edits) e.accept(b);
        return b.build();
    }

    /** Mutable stand-in for the filter record so each test names only what it sets. */
    private static final class Builder {
        YearMonth month;
        LocalDate dateFrom, dateTo;
        Long cardFrom, cardTo, departmentId;
        EmployeeStatusFilter status;
        Integer page, size;
        String search;

        Builder status(EmployeeStatusFilter s) {
            status = s;
            return this;
        }

        Builder month(YearMonth m) {
            month = m;
            return this;
        }

        AccountsReportFilter build() {
            return new AccountsReportFilter(null, month, dateFrom, dateTo, null, cardFrom, cardTo, null,
                    departmentId, null, null, status, null, null, search, null, null, page, size);
        }
    }
}
