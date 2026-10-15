package com.forward.hrms.payroll.service;

import com.forward.hrms.commons.cache.CatalogCache;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.core.error.ValidationError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.employee.dto.response.DepartmentDto;
import com.forward.hrms.employee.enums.EmploymentStatus;
import com.forward.hrms.employee.enums.GradeType;
import com.forward.hrms.payroll.dto.request.AccountsReportFilter;
import com.forward.hrms.payroll.dto.response.AccountsReportColumnDto;
import com.forward.hrms.payroll.dto.response.AccountsReportDefinitionDto;
import com.forward.hrms.payroll.dto.response.AccountsReportResultDto;
import com.forward.hrms.payroll.entity.SalaryComponent;
import com.forward.hrms.payroll.enums.ComponentType;
import com.forward.hrms.payroll.enums.EmployeeStatusFilter;
import com.forward.hrms.payroll.enums.ReportExportFormat;
import com.forward.hrms.payroll.repository.AllowanceReportRepository;
import com.forward.hrms.payroll.repository.SalaryComponentRepository;
import com.forward.hrms.payroll.repository.projection.EmployeeAmountProjection;
import com.forward.hrms.payroll.repository.projection.EmployeeCodeAmountProjection;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Allowance accounts reports (F-1 … F-6) end to end through
 * {@link AccountsReportService}.
 *
 * <p>Fixture, August 2026: Special — card 20 has 5,000 on a (legacy) payslip
 * line and 500 from the allowance run (5,500 in all), card 0101 has 3,000
 * from the run, card 5 has 0 from the run. Meal — card 0101 2,000 and card 5
 * (terminated) 1,500, both from the run. Nothing else, nothing in July.
 */
class AllowanceAccountsReportsTest {

    private final AllowanceReportRepository repo = mock(AllowanceReportRepository.class);
    private final SalaryComponentRepository components = mock(SalaryComponentRepository.class);
    private final AccountsReportEmployees employees = mock(AccountsReportEmployees.class);
    private final CatalogCache catalog = mock(CatalogCache.class);
    private final AuditEmitter audit = mock(AuditEmitter.class);

    private AccountsReportService service;

    @BeforeEach
    void setUp() {
        service = new AccountsReportService(List.of(new AllowanceReportSource(repo, components)), employees, catalog,
                audit);

        when(repo.sumPayslipLines(anyInt(), anyInt(), anyString())).thenReturn(List.of());
        when(repo.sumAllowanceRun(anyInt(), anyInt(), anyString())).thenReturn(List.of());
        when(repo.sumPayslipLines(2026, 8, "SPECIAL")).thenReturn(List.of(amount(102, "5000")));
        when(repo.sumAllowanceRun(2026, 8, "SPECIAL")).thenReturn(List.of(
                amount(101, "3000"), amount(102, "500"), amount(103, "0")));
        when(repo.sumAllowanceRun(2026, 8, "MEAL")).thenReturn(List.of(amount(101, "2000"), amount(103, "1500")));

        // Sheet (F-7): the same amounts, per code.
        when(components.filter(ComponentType.EARNING, true)).thenReturn(List.of(
                component("SPECIAL", "Special Allowance"), component("MEAL", "Meal Allowance")));
        when(repo.sumPayslipLinesByCode(anyInt(), anyInt(), anyCollection())).thenReturn(List.of());
        when(repo.sumAllowanceRunByCode(anyInt(), anyInt(), anyCollection())).thenReturn(List.of());
        when(repo.sumPayslipLinesByCode(eq(2026), eq(8), anyCollection()))
                .thenReturn(List.of(amount(102, "SPECIAL", "5000")));
        when(repo.sumAllowanceRunByCode(eq(2026), eq(8), anyCollection())).thenReturn(List.of(
                amount(101, "SPECIAL", "3000"), amount(102, "SPECIAL", "500"), amount(103, "SPECIAL", "0"),
                amount(101, "MEAL", "2000"), amount(103, "MEAL", "1500")));

        Map<Long, AccountsReportEmployee> emps = new HashMap<>();
        emps.put(101L, employee(101, "0101", "Ali", EmploymentStatus.ACTIVE, 1L, "Production"));
        emps.put(102L, employee(102, "20", "Bilal", EmploymentStatus.ACTIVE, 2L, "Admin", GradeType.MANAGEMENT));
        emps.put(103L, employee(103, "5", "Camran", EmploymentStatus.TERMINATED, 1L, "Production"));
        when(employees.resolve(anyCollection())).thenReturn(emps);

        when(catalog.snapshot()).thenReturn(new CatalogCache.CatalogIndex(
                Map.of(1L, new DepartmentDto(1, "PROD", "Production", null, true, true)),
                Map.of(), Map.of(), Map.of(), Map.of()));
    }

    @Test
    void special_sumsPayslipLinesAndAllowanceRun_skipsZero() {
        AccountsReportResultDto f1 = run("F-1", filter());
        assertThat(f1.rows()).extracting(r -> r.get("cardNo"), r -> r.get("amount"))
                .containsExactly(tuple("20", new BigDecimal("5500")), tuple("0101", new BigDecimal("3000")));
        assertThat(f1.totals()).containsEntry("amount", new BigDecimal("8500"));
        assertThat(f1.totalEmployees()).isEqualTo(2);
        assertThat(f1.columns()).extracting(AccountsReportColumnDto::label).last().isEqualTo("Special Allowance");
    }

    @Test
    void eachReportReadsItsOwnComponent() {
        assertThat(run("F-2", filter()).rows()).extracting(r -> r.get("cardNo"), r -> r.get("amount"))
                .containsExactly(tuple("5", new BigDecimal("1500")), tuple("0101", new BigDecimal("2000")));
        for (String id : List.of("F-3", "F-4", "F-5", "F-6")) {
            assertThat(run(id, filter()).rows()).as(id).isEmpty();
        }
    }

    @Test
    void otherMonth_isEmpty() {
        assertThat(run("F-1", filter(x -> x.month = YearMonth.of(2026, 7))).rows()).isEmpty();
    }

    @Test
    void employeeStatus_activeNonActiveAll() {
        assertThat(cards("F-2", filter(x -> x.status = EmployeeStatusFilter.ACTIVE))).containsExactly("0101");
        assertThat(cards("F-2", filter(x -> x.status = EmployeeStatusFilter.NON_ACTIVE))).containsExactly("5");
        assertThat(cards("F-2", filter())).hasSize(2);
    }

    @Test
    void departmentAndCardRange_combine_withLeadingZeroCards() {
        assertThat(cards("F-2", filter(x -> x.departmentId = 1L, x -> x.cardFrom = 1L, x -> x.cardTo = 50L)))
                .containsExactly("5");
        assertThat(cards("F-2", filter(x -> x.departmentId = 1L, x -> x.cardFrom = 100L, x -> x.cardTo = 200L)))
                .containsExactly("0101");
        AccountsReportResultDto r = run("F-1", filter(x -> x.departmentId = 1L));
        assertThat(r.appliedFilters()).contains("Department: Production").contains("Month: August 2026");
    }

    @Test
    void validation_returns400_andUnknownIdIs404() {
        assertBadRequest("F-1", filter(x -> x.month = null), "month");
        assertBadRequest("F-4", filter(x -> x.cardFrom = 90L, x -> x.cardTo = 10L), "cardFrom");
        assertThatThrownBy(() -> run("F-8", filter())).isInstanceOf(NotFoundError.class);
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
    void registry_usesLegacyIds() {
        assertThat(service.registry()).extracting(AccountsReportDefinitionDto::id)
                .containsExactly("F-1", "F-2", "F-3", "F-4", "F-5", "F-6", "F-7");
        assertThat(service.registry().getFirst().params())
                .extracting(p -> p.param().name() + (p.required() ? "*" : ""))
                .containsExactlyInAnyOrder("MONTH*", "CARD_RANGE", "DEPARTMENT", "SECTION", "DESIGNATION",
                        "EMP_STATUS");
        // The constant name is accepted too.
        assertThat(run("F1", filter()).reportId()).isEqualTo("F-1");
    }

    @Test
    void excelAndCsvTotals_matchScreen() throws Exception {
        AccountsReportFilter f = filter();
        AccountsReportResultDto screen = run("F-1", f);
        int amountCol = 6; // Sr, Card, Name, Dept, Section, Designation, Amount

        AccountsReportService.Exported xlsx = service.export("F-1", f, ReportExportFormat.XLSX);
        assertThat(xlsx.filename()).startsWith("F-1-special-allowance-");
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx.body()))) {
            XSSFSheet sheet = wb.getSheetAt(0);
            XSSFRow total = sheet.getRow(sheet.getLastRowNum());
            assertThat(total.getCell(0).getStringCellValue()).isEqualTo("Total");
            assertThat(total.getCell(1).getStringCellValue()).isEqualTo("2 employee(s)");
            assertThat(BigDecimal.valueOf(total.getCell(amountCol).getNumericCellValue()))
                    .isEqualByComparingTo(screen.totals().get("amount"));
            assertThat(sheet.getRow(3).getCell(0).getStringCellValue()).startsWith("Printed ");
        }

        AccountsReportService.Exported csv = service.export("F-1", f, ReportExportFormat.CSV);
        List<String> csvLines = new String(csv.body(), StandardCharsets.UTF_8).lines().toList();
        assertThat(csvLines).hasSize(screen.rows().size() + 2);
        String[] totalCells = csvLines.getLast().split(",", -1);
        assertThat(new BigDecimal(totalCells[amountCol])).isEqualByComparingTo(screen.totals().get("amount"));
    }

    // ---------- allowances calculation sheet (F-7) ----------

    @Test
    void sheet_hasOneColumnPerCatalogEarning_beforeTotal() {
        AccountsReportDefinitionDto f7 = service.registry().stream().filter(d -> d.id().equals("F-7")).findFirst()
                .orElseThrow();
        assertThat(f7.columns()).extracting(AccountsReportColumnDto::label).containsExactly(
                "Sr No", "Card No", "Employee Name", "Department", "Section", "Designation", "Grade",
                "Date of Joining", "Special Allowance", "Meal Allowance", "Total", "Employee Signature");
        assertThat(f7.printable()).isTrue();
        assertThat(f7.params()).extracting(p -> p.param().name() + (p.required() ? "*" : ""))
                .containsExactlyInAnyOrder("MONTH*", "CARD_RANGE", "CARD_LIST", "DEPARTMENT", "SECTION",
                        "DESIGNATION", "EMP_STATUS", "GRADE_TYPE");
    }

    @Test
    void sheet_allEmployees_rowPerEmployee_totalsPerComponent() {
        AccountsReportResultDto f8 = run("F-7", filter());
        assertThat(f8.rows()).extracting(r -> r.get("cardNo"), r -> r.get("SPECIAL"), r -> r.get("MEAL"),
                r -> r.get("total")).containsExactly(
                tuple("5", new BigDecimal("0"), new BigDecimal("1500"), new BigDecimal("1500")),
                tuple("20", new BigDecimal("5500"), BigDecimal.ZERO, new BigDecimal("5500")),
                tuple("0101", new BigDecimal("3000"), new BigDecimal("2000"), new BigDecimal("5000")));
        assertThat(f8.totals()).containsEntry("SPECIAL", new BigDecimal("8500"))
                .containsEntry("MEAL", new BigDecimal("3500")).containsEntry("total", new BigDecimal("12000"));
        assertThat(f8.totalEmployees()).isEqualTo(3);
    }

    @Test
    void sheet_gradeTypeFilter() {
        assertThat(cards("F-7", filter(x -> x.gradeType = GradeType.MANAGEMENT))).containsExactly("20");
        assertThat(cards("F-7", filter(x -> x.gradeType = GradeType.LABOUR))).containsExactly("5", "0101");
        assertThat(cards("F-7", filter(x -> x.gradeType = GradeType.SUPERVISOR))).isEmpty();
        assertThat(run("F-7", filter(x -> x.gradeType = GradeType.MANAGEMENT)).appliedFilters())
                .contains("Grade Type: Management");
        // Reports without the filter ignore it.
        assertThat(cards("F-2", filter(x -> x.gradeType = GradeType.MANAGEMENT))).hasSize(2);
    }

    @Test
    void sheet_newCatalogComponent_becomesAColumn_andSortable() {
        when(components.filter(ComponentType.EARNING, true)).thenReturn(List.of(
                component("SPECIAL", "Special Allowance"), component("MEAL", "Meal Allowance"),
                component("MOBILE", "Mobile Allowance")));
        AccountsReportResultDto f8 = run("F-7", filter(x -> x.sortBy = "MOBILE"));
        assertThat(f8.columns()).extracting(AccountsReportColumnDto::key).contains("MOBILE");
        assertThat(f8.rows()).allSatisfy(r -> assertThat(r.get("MOBILE")).isEqualTo(BigDecimal.ZERO));
        assertThat(f8.totals()).containsEntry("MOBILE", BigDecimal.ZERO);
        assertBadRequest("F-7", filter(x -> x.sortBy = "HRA"), "sortBy");
    }

    @Test
    void sheet_excelHasComponentColumns_andTotals() throws Exception {
        AccountsReportService.Exported xlsx = service.export("F-7", filter(), ReportExportFormat.XLSX);
        assertThat(xlsx.filename()).startsWith("F-7-allowances-calculation-");
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx.body()))) {
            XSSFSheet sheet = wb.getSheetAt(0);
            XSSFRow total = sheet.getRow(sheet.getLastRowNum());
            // Sr, Card, Name, Dept, Section, Designation, Grade, DOJ, Special, Meal, Total, Signature
            assertThat(BigDecimal.valueOf(total.getCell(8).getNumericCellValue())).isEqualByComparingTo("8500");
            assertThat(BigDecimal.valueOf(total.getCell(10).getNumericCellValue())).isEqualByComparingTo("12000");
        }
    }

    // ---------- fixture helpers ----------

    private AccountsReportResultDto run(String id, AccountsReportFilter f) {
        return service.data(id, f);
    }

    private List<Object> cards(String id, AccountsReportFilter f) {
        return run(id, f).rows().stream().map(r -> r.get("cardNo")).toList();
    }

    private static EmployeeAmountProjection amount(long employeeId, String amount) {
        return new EmployeeAmountProjection() {
            @Override
            public Long getEmployeeId() {
                return employeeId;
            }

            @Override
            public BigDecimal getAmount() {
                return new BigDecimal(amount);
            }
        };
    }

    private static EmployeeCodeAmountProjection amount(long employeeId, String code, String amount) {
        return new EmployeeCodeAmountProjection() {
            @Override
            public Long getEmployeeId() {
                return employeeId;
            }

            @Override
            public String getCode() {
                return code;
            }

            @Override
            public BigDecimal getAmount() {
                return new BigDecimal(amount);
            }
        };
    }

    private static SalaryComponent component(String code, String name) {
        SalaryComponent c = new SalaryComponent();
        c.setCode(code);
        c.setName(name);
        c.setType(ComponentType.EARNING);
        return c;
    }

    private static AccountsReportEmployee employee(long id, String card, String name, EmploymentStatus status,
                                                   Long departmentId, String department) {
        return employee(id, card, name, status, departmentId, department, GradeType.LABOUR);
    }

    private static AccountsReportEmployee employee(long id, String card, String name, EmploymentStatus status,
                                                   Long departmentId, String department, GradeType gradeType) {
        return new AccountsReportEmployee(id, card, name, "Father of " + name, null, LocalDate.of(2020, 1, 1),
                status, "PERMANENT", departmentId, department, null, null, null, null, null, null, null,
                gradeType);
    }

    @SafeVarargs
    private static AccountsReportFilter filter(Consumer<Builder>... edits) {
        Builder b = new Builder();
        b.month = YearMonth.of(2026, 8);
        b.status = EmployeeStatusFilter.ALL;
        for (var e : edits) e.accept(b);
        return b.build();
    }

    /** Mutable stand-in for the filter record so each test names only what it sets. */
    private static final class Builder {
        YearMonth month;
        Long cardFrom, cardTo, departmentId;
        EmployeeStatusFilter status;
        String sortBy;
        GradeType gradeType;

        AccountsReportFilter build() {
            return new AccountsReportFilter(null, month, null, null, null, cardFrom, cardTo, null,
                    departmentId, null, null, status, null, null, null, sortBy, null, null, null,
                    null, null, null, gradeType);
        }
    }
}
