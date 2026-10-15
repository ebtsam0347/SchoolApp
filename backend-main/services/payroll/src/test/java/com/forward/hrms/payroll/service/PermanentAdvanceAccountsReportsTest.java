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
import com.forward.hrms.payroll.entity.PermanentAdvance;
import com.forward.hrms.payroll.entity.PermanentAdvanceDeduction;
import com.forward.hrms.payroll.enums.AdvanceBalanceFilter;
import com.forward.hrms.payroll.enums.AdvanceStatus;
import com.forward.hrms.payroll.enums.PermanentAdvanceDeductionSource;
import com.forward.hrms.payroll.enums.EmployeeStatusFilter;
import com.forward.hrms.payroll.enums.ReportExportFormat;
import com.forward.hrms.payroll.repository.PermanentAdvanceReportRepository;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Permanent-advance accounts reports (C1–C11) end to end through
 * {@link AccountsReportService} with a mocked ledger and employee lookup.
 *
 * <p>Fixture: card 101 (active, dept 1) took 10,000 in Jan-2026 at 1,000/month
 * with 2,000 against annual leave and 3,000 against bonus 1, recovered 1,000
 * in Jan and 1,000 salary + 2,000 annual leave in Feb. Card 20 (terminated,
 * dept 2) took 5,000 in Dec-2025, fully recovered in Jan (closed). Card 5
 * (active, dept 1) took 8,000 on 1-Feb-2026 at 800/month, nothing recovered.
 */
class PermanentAdvanceAccountsReportsTest {

    private final PermanentAdvanceReportRepository repo = mock(PermanentAdvanceReportRepository.class);
    private final AccountsReportEmployees employees = mock(AccountsReportEmployees.class);
    private final CatalogCache catalog = mock(CatalogCache.class);
    private final AuditEmitter audit = mock(AuditEmitter.class);

    private AccountsReportService service;

    @BeforeEach
    void setUp() {
        service = new AccountsReportService(List.of(new PermanentAdvanceReportSource(repo)), employees, catalog, audit);

        when(repo.findPaid()).thenReturn(List.of(
                advance(1, 101, "10000", "1000", LocalDate.of(2026, 1, 10), AdvanceStatus.APPROVED, "2000", "3000"),
                settled(advance(2, 102, "5000", "500", LocalDate.of(2025, 12, 5), AdvanceStatus.APPROVED, "0", "0")),
                advance(3, 103, "8000", "800", LocalDate.of(2026, 2, 1), AdvanceStatus.APPROVED, "0", "0")));
        when(repo.findAllDeductions()).thenReturn(List.of(
                deduction(11, 101, LocalDate.of(2026, 1, 31), "1000", PermanentAdvanceDeductionSource.SALARY),
                deduction(12, 101, LocalDate.of(2026, 2, 28), "1000", PermanentAdvanceDeductionSource.SALARY),
                deduction(14, 101, LocalDate.of(2026, 2, 28), "2000", PermanentAdvanceDeductionSource.ANNUAL_LEAVE),
                deduction(13, 102, LocalDate.of(2026, 1, 31), "5000", PermanentAdvanceDeductionSource.SALARY)));

        Map<Long, AccountsReportEmployee> emps = new HashMap<>();
        emps.put(101L, employee(101, "101", "Ali", EmploymentStatus.ACTIVE, 1L));
        emps.put(102L, employee(102, "20", "Bilal", EmploymentStatus.TERMINATED, 2L));
        emps.put(103L, employee(103, "5", "Camran", EmploymentStatus.ON_NOTICE, 1L));
        when(employees.resolve(anyCollection())).thenReturn(emps);

        when(catalog.snapshot()).thenReturn(new CatalogCache.CatalogIndex(
                Map.of(1L, new DepartmentDto(1, "PROD", "Production", null, true, true)),
                Map.of(), Map.of(), Map.of(), Map.of()));
    }

    // ---------- each report on its own transaction type ----------

    @Test
    void paidAnnualLeaveBonusAndReceived_returnDifferentRows() {
        AccountsReportResultDto c1 = run("C1", month(2026, 1, EmployeeStatusFilter.ALL));
        AccountsReportResultDto c6 = run("C6", month(2026, 1, EmployeeStatusFilter.ALL));
        AccountsReportResultDto c11 = run("C11", month(2026, 1, EmployeeStatusFilter.ALL));
        AccountsReportResultDto c7 = run("C7", month(2026, 1, EmployeeStatusFilter.ALL));

        assertThat(c1.rows()).singleElement().satisfies(r -> {
            assertThat(r.get("cardNo")).isEqualTo("101");
            assertThat(r.get("advanceAmount")).isEqualTo(new BigDecimal("10000"));
        });
        assertThat(c6.rows()).singleElement()
                .satisfies(r -> assertThat(r.get("advanceAmount")).isEqualTo(new BigDecimal("2000")));
        assertThat(c11.rows()).singleElement().satisfies(r -> {
            assertThat(r.get("amountAgainstBonus")).isEqualTo(new BigDecimal("3000"));
            assertThat(r.get("bonusNo")).isEqualTo(1);
            assertThat(r.get("financialPeriod")).isEqualTo("2025-26");
        });
        assertThat(c7.rows()).extracting(r -> r.get("amountReceived"))
                .containsExactly(new BigDecimal("5000"), new BigDecimal("1000"));
        assertThat(c7.totals()).containsEntry("amountReceived", new BigDecimal("6000"));
    }

    @Test
    void paidAgainstBonus_honoursBonusNo() {
        AccountsReportFilter bonus2 = filter(f -> f.month = YearMonth.of(2026, 1), f -> f.bonusNo = 2);
        assertThat(run("C11", bonus2).rows()).isEmpty();
    }

    @Test
    void deductedAgainstAnnualLeave_onlyAnnualLeaveRecoveries() {
        AccountsReportResultDto c8 = run("C8", month(2026, 2, EmployeeStatusFilter.ALL));
        assertThat(c8.rows()).singleElement().satisfies(r -> {
            assertThat(r.get("amountDeducted")).isEqualTo(new BigDecimal("2000"));
            assertThat(r.get("transactionDate")).isEqualTo(LocalDate.of(2026, 2, 28));
        });
    }

    // ---------- employee status is honoured, including ALL ----------

    @Test
    void employeeStatus_activeNonActiveAll() {
        assertThat(run("C1", month(2025, 12, EmployeeStatusFilter.ACTIVE)).rows()).isEmpty();
        assertThat(run("C1", month(2025, 12, EmployeeStatusFilter.NON_ACTIVE)).rows()).hasSize(1);
        assertThat(run("C1", month(2025, 12, EmployeeStatusFilter.ALL)).rows()).hasSize(1);
        // ON_NOTICE counts as active.
        assertThat(run("C1", month(2026, 2, EmployeeStatusFilter.ACTIVE)).rows()).hasSize(1);
    }

    // ---------- balances ----------

    @Test
    void balance_greaterThanZeroAndZero() {
        AccountsReportResultDto open = run("C2", filter(f -> f.balance = AdvanceBalanceFilter.GREATER_THAN_ZERO));
        assertThat(open.rows()).extracting(r -> r.get("cardNo"), r -> r.get("balance"), r -> r.get("recoveredAmount"))
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("5", new BigDecimal("8000"), BigDecimal.ZERO),
                        org.assertj.core.groups.Tuple.tuple("101", new BigDecimal("6000"), new BigDecimal("4000")));
        assertThat(open.totals()).containsEntry("balance", new BigDecimal("14000"));

        AccountsReportResultDto closed = run("C2", filter(f -> f.balance = AdvanceBalanceFilter.EQUAL_ZERO));
        assertThat(closed.rows()).singleElement().satisfies(r -> assertThat(r.get("cardNo")).isEqualTo("20"));
    }

    @Test
    void balanceByDate_isOutstandingAsOfRangeEnd() {
        AccountsReportResultDto c3 = run("C3", filter(
                f -> f.dateFrom = LocalDate.of(2026, 1, 1), f -> f.dateTo = LocalDate.of(2026, 1, 31)));
        // Card 5's advance is dated 1-Feb (after the range end); card 20 is fully recovered by 31-Jan.
        assertThat(c3.rows()).singleElement().satisfies(r -> {
            assertThat(r.get("cardNo")).isEqualTo("101");
            assertThat(r.get("totalAdvance")).isEqualTo(new BigDecimal("10000"));
            assertThat(r.get("totalDeduction")).isEqualTo(new BigDecimal("1000"));
            assertThat(r.get("balance")).isEqualTo(new BigDecimal("9000"));
        });
    }

    @Test
    void totalPaidAsOn_includesSettledEmployees() {
        AccountsReportResultDto c4 = run("C4", filter(f -> f.asOnDate = LocalDate.of(2026, 1, 31)));
        assertThat(c4.rows()).extracting(r -> r.get("cardNo")).containsExactly("20", "101");
    }

    @Test
    void expectedReturn_monthsRoundedUp() {
        var rows = PermanentAdvanceReportSource.expectedReturn(repo.findPaid(), repo.findAllDeductions(),
                YearMonth.of(2026, 9));
        assertThat(rows).extracting(r -> r.employeeId(), r -> r.values().get("expectedMonths"),
                        r -> r.values().get("expectedCompletion"))
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(101L, 6, "March 2027"),
                        org.assertj.core.groups.Tuple.tuple(103L, 10, "July 2027"));
    }

    @Test
    void zeroDeduction_outstandingButNoSalaryDeductionThatMonth() {
        AccountsReportResultDto c10 = run("C10", month(2026, 2, EmployeeStatusFilter.ALL));
        assertThat(c10.rows()).singleElement().satisfies(r -> {
            assertThat(r.get("cardNo")).isEqualTo("5");
            assertThat(r.get("advanceBalance")).isEqualTo(new BigDecimal("8000"));
            assertThat(r.get("deductionRate")).isEqualTo(new BigDecimal("800"));
        });
    }

    // ---------- ledger ----------

    @Test
    void ledger_runningBalanceAndHeader() {
        AccountsReportResultDto c5 = run("C5", filter(f -> f.cardList = List.of("0101")));
        assertThat(c5.rows()).extracting(r -> r.get("runningBalance"))
                .containsExactly(new BigDecimal("10000"), new BigDecimal("9000"), new BigDecimal("8000"),
                        new BigDecimal("6000"));
        // February's salary and annual-leave recoveries are separate rows, each with its own source.
        assertThat(c5.rows().get(2).get("amountRecovered")).isEqualTo(new BigDecimal("1000"));
        assertThat(c5.rows().get(3).get("amountRecovered")).isEqualTo(new BigDecimal("2000"));
        assertThat(c5.header()).containsEntry("employeeName", "Ali");
        assertThat(c5.totals()).containsEntry("runningBalance", new BigDecimal("6000"));
        assertThat(c5.columns()).noneMatch(c -> c.key().equals("srNo"));
    }

    // ---------- org / card filters ----------

    @Test
    void cardRangeAndDepartment_combine() {
        AccountsReportFilter f = filter(x -> x.balance = AdvanceBalanceFilter.GREATER_THAN_ZERO,
                x -> x.cardFrom = 1L, x -> x.cardTo = 50L, x -> x.departmentId = 1L);
        AccountsReportResultDto r = run("C2", f);
        assertThat(r.rows()).extracting(x -> x.get("cardNo")).containsExactly("5");
        assertThat(r.appliedFilters()).contains("Department: Production").contains("Cards: 1 - 50");
    }

    @Test
    void srNoAndPaging_totalsCoverAllPages() {
        AccountsReportFilter f = filter(x -> x.balance = AdvanceBalanceFilter.GREATER_THAN_ZERO,
                x -> x.page = 1, x -> x.size = 1);
        AccountsReportResultDto r = run("C2", f);
        assertThat(r.rows()).singleElement().satisfies(x -> {
            assertThat(x.get("srNo")).isEqualTo(2);
            assertThat(x.get("cardNo")).isEqualTo("101");
        });
        assertThat(r.totalElements()).isEqualTo(2);
        assertThat(r.totalEmployees()).isEqualTo(2);
        assertThat(r.totals()).containsEntry("balance", new BigDecimal("14000"));
    }

    @Test
    void sortByColumnDescending() {
        AccountsReportFilter f = filter(x -> x.balance = AdvanceBalanceFilter.GREATER_THAN_ZERO,
                x -> x.sortBy = "balance", x -> x.sortDir = "desc");
        assertThat(run("C2", f).rows()).extracting(x -> x.get("balance"))
                .containsExactly(new BigDecimal("8000"), new BigDecimal("6000"));
    }

    // ---------- validation ----------

    @Test
    void validation_requiredAndRanges() {
        assertThatThrownBy(() -> run("C1", filter()))
                .isInstanceOf(ValidationError.class)
                .satisfies(e -> assertThat(((ValidationError) e).violations())
                        .extracting(v -> v.field()).contains("month"));
        assertThatThrownBy(() -> run("C1", new Builder().status(null).month(YearMonth.of(2026, 1)).build()))
                .isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> run("C2", filter(x -> x.balance = AdvanceBalanceFilter.EQUAL_ZERO,
                x -> x.cardFrom = 50L, x -> x.cardTo = 10L))).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> run("C3", filter(x -> x.dateFrom = LocalDate.of(2026, 2, 1),
                x -> x.dateTo = LocalDate.of(2026, 1, 1)))).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> run("C5", filter(x -> x.cardList = List.of("101", "5"))))
                .isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> run("C2", filter(x -> x.balance = AdvanceBalanceFilter.EQUAL_ZERO,
                x -> x.sortBy = "salary"))).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> run("Z9", filter())).isInstanceOf(NotFoundError.class);
    }

    // ---------- registry / export ----------

    @Test
    void registry_listsPermanentAdvanceReports() {
        assertThat(service.registry()).extracting(AccountsReportDefinitionDto::id)
                .containsExactly("C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10", "C11");
    }

    @Test
    void export_matchesScreenRowsAndTotals() throws Exception {
        AccountsReportFilter f = filter(x -> x.balance = AdvanceBalanceFilter.GREATER_THAN_ZERO);
        AccountsReportService.Exported xlsx = service.export("C2", f, ReportExportFormat.XLSX);
        assertThat(xlsx.filename()).startsWith("C2-permanent-advance-balance-").endsWith(".xlsx");
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx.body()))) {
            XSSFSheet sheet = wb.getSheetAt(0);
            int last = sheet.getLastRowNum();
            // Header row, 2 data rows, total row at the bottom.
            assertThat(sheet.getRow(last).getCell(0).getStringCellValue()).isEqualTo("Total");
            assertThat(sheet.getRow(last).getCell(1).getStringCellValue()).isEqualTo("2 employee(s)");
            assertThat(sheet.getRow(last).getCell(8).getNumericCellValue()).isEqualTo(14000d);
            assertThat(sheet.getRow(last - 1).getCell(1).getStringCellValue()).isEqualTo("101");
        }

        AccountsReportService.Exported csv = service.export("C2", f, ReportExportFormat.CSV);
        String text = new String(csv.body(), StandardCharsets.UTF_8);
        assertThat(text).startsWith("﻿Sr No,Card No,");
        assertThat(text.lines()).hasSize(4);
        assertThat(text.lines().toList().getLast()).startsWith("Total,2 employee(s),").contains("14000");
    }

    // ---------- fixture helpers ----------

    private AccountsReportResultDto run(String id, AccountsReportFilter f) {
        return service.data(id, f);
    }

    /** Recovered by final settlement: still APPROVED, closed_at set. */
    private static PermanentAdvance settled(PermanentAdvance a) {
        a.setClosedAt(java.time.LocalDateTime.of(2026, 1, 31, 0, 0));
        return a;
    }

    private static PermanentAdvance advance(long id, long employeeId, String amount, String rate, LocalDate received,
                                            AdvanceStatus status, String annualLeaveOffset, String bonusOffset) {
        PermanentAdvance a = new PermanentAdvance();
        a.setId(id);
        a.setRequestCode("PA-" + id);
        a.setEmployeeId(employeeId);
        a.setAmount(new BigDecimal(amount));
        a.setMonthlyDeduction(new BigDecimal(rate));
        a.setReceivingDate(received);
        a.setStatus(status);
        a.setAnnualLeaveOffset(new BigDecimal(annualLeaveOffset));
        a.setBonusOffset(new BigDecimal(bonusOffset));
        a.setFiscalYearCode("2025-26");
        return a;
    }

    private static PermanentAdvanceDeduction deduction(long id, long employeeId, LocalDate date, String amount,
                                                       PermanentAdvanceDeductionSource source) {
        PermanentAdvanceDeduction d = new PermanentAdvanceDeduction();
        d.setId(id);
        d.setEmployeeId(employeeId);
        d.setTransactionDate(date);
        d.setDeductedAmount(new BigDecimal(amount));
        d.setSource(source);
        return d;
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

    @SafeVarargs
    private static AccountsReportFilter filter(java.util.function.Consumer<Builder>... edits) {
        Builder b = new Builder().status(EmployeeStatusFilter.ALL);
        for (var e : edits) e.accept(b);
        return b.build();
    }

    /** Mutable stand-in for the filter record so each test names only what it sets. */
    private static final class Builder {
        YearMonth month;
        LocalDate dateFrom, dateTo, asOnDate;
        Long cardFrom, cardTo, departmentId;
        List<String> cardList;
        EmployeeStatusFilter status;
        AdvanceBalanceFilter balance;
        Integer bonusNo, page, size;
        String sortBy, sortDir;

        Builder status(EmployeeStatusFilter s) {
            status = s;
            return this;
        }

        Builder month(YearMonth m) {
            month = m;
            return this;
        }

        AccountsReportFilter build() {
            return new AccountsReportFilter(null, month, dateFrom, dateTo, asOnDate, cardFrom, cardTo, cardList,
                    departmentId, null, null, status, balance, bonusNo, null, sortBy, sortDir, page, size);
        }
    }
}
