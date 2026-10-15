package com.forward.hrms.payroll.integration;

import com.forward.hrms.commons.cache.CatalogCache;
import com.forward.hrms.commons.cache.EmployeeDisplayCache;
import com.forward.hrms.commons.testsupport.containers.WithTestcontainers;
import com.forward.hrms.employee.dto.response.DepartmentDto;
import com.forward.hrms.employee.enums.EmploymentStatus;
import com.forward.hrms.payroll.client.AttendanceClient;
import com.forward.hrms.payroll.client.EmployeeClient;
import com.forward.hrms.payroll.entity.AllowanceRun;
import com.forward.hrms.payroll.entity.AllowanceTransaction;
import com.forward.hrms.payroll.entity.Bank;
import com.forward.hrms.payroll.entity.BonusNoSettings;
import com.forward.hrms.payroll.entity.BonusTransaction;
import com.forward.hrms.payroll.entity.EmployeeBankDetails;
import com.forward.hrms.payroll.entity.EmployeePayrollSettings;
import com.forward.hrms.payroll.entity.EmployeeSalaryComponent;
import com.forward.hrms.payroll.entity.PayrollRun;
import com.forward.hrms.payroll.entity.Payslip;
import com.forward.hrms.payroll.entity.PayslipLine;
import com.forward.hrms.payroll.entity.PermanentAdvance;
import com.forward.hrms.payroll.entity.PermanentAdvanceDeduction;
import com.forward.hrms.payroll.entity.SalaryComponent;
import com.forward.hrms.payroll.entity.ShortTermAdvance;
import com.forward.hrms.payroll.entity.ShortTermAdvanceBatch;
import com.forward.hrms.payroll.enums.AdvanceStatus;
import com.forward.hrms.payroll.enums.AllowanceRunStatus;
import com.forward.hrms.payroll.enums.BonusNoSettingsStatus;
import com.forward.hrms.payroll.enums.BonusTransactionPaymentMode;
import com.forward.hrms.payroll.enums.CalcType;
import com.forward.hrms.payroll.enums.ComponentType;
import com.forward.hrms.payroll.enums.FiscalYearStatus;
import com.forward.hrms.payroll.enums.PaymentMode;
import com.forward.hrms.payroll.enums.PayrollRunStatus;
import com.forward.hrms.payroll.enums.ShortTermAdvanceStatus;
import com.forward.hrms.payroll.repository.AllowanceRunRepository;
import com.forward.hrms.payroll.repository.AllowanceTransactionRepository;
import com.forward.hrms.payroll.repository.BankRepository;
import com.forward.hrms.payroll.repository.BonusNoSettingsRepository;
import com.forward.hrms.payroll.repository.BonusTransactionRepository;
import com.forward.hrms.payroll.repository.EmployeeBankDetailsRepository;
import com.forward.hrms.payroll.repository.EmployeePayrollSettingsRepository;
import com.forward.hrms.payroll.repository.EmployeeSalaryComponentRepository;
import com.forward.hrms.payroll.repository.FiscalYearRepository;
import com.forward.hrms.payroll.repository.PayrollRunRepository;
import com.forward.hrms.payroll.repository.PayslipLineRepository;
import com.forward.hrms.payroll.repository.PayslipRepository;
import com.forward.hrms.payroll.repository.PermanentAdvanceDeductionRepository;
import com.forward.hrms.payroll.repository.PermanentAdvanceRepository;
import com.forward.hrms.payroll.repository.SalaryComponentRepository;
import com.forward.hrms.payroll.repository.ShortTermAdvanceBatchRepository;
import com.forward.hrms.payroll.repository.ShortTermAdvanceRepository;
import com.forward.hrms.payroll.service.AccountsReportEmployee;
import com.forward.hrms.payroll.service.AccountsReportEmployees;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Accounts reports over real HTTP and a real Postgres: the DispatcherServlet,
 * the {@code payroll.reports.run} gate, JSON binding of the filter (e.g.
 * {@code "month": "2031-08"}), every repository query against the migrated
 * schema, RFC 7807 errors, Excel / CSV export and the PDF documents.
 * Only employee-service is stubbed (employee lookup, catalog, attendance).
 *
 * <p>Seeds one fixture per report family in August 2031 (a period no other
 * test uses): two employees — card {@code 20} (bank transfer at a test
 * bank) and card {@code 0101} (cash) — with a posted payroll run, an
 * allowance run, a permanent advance ledger, a disbursed temporary advance
 * batch and a computed bonus.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithTestcontainers
class AccountsReportsHttpIntegrationTest {

    private static final String BASE = "/reports/payroll/accounts";

    @Autowired MockMvc mvc;
    @Autowired FiscalYearRepository fiscalYears;
    @Autowired BankRepository banks;
    @Autowired EmployeePayrollSettingsRepository settings;
    @Autowired EmployeeBankDetailsRepository bankDetails;
    @Autowired PayrollRunRepository runs;
    @Autowired PayslipRepository payslips;
    @Autowired PayslipLineRepository lines;
    @Autowired PermanentAdvanceRepository permanentAdvances;
    @Autowired PermanentAdvanceDeductionRepository permanentDeductions;
    @Autowired ShortTermAdvanceBatchRepository batches;
    @Autowired ShortTermAdvanceRepository shortTermAdvances;
    @Autowired BonusNoSettingsRepository bonusSettings;
    @Autowired BonusTransactionRepository bonusTransactions;
    @Autowired SalaryComponentRepository components;
    @Autowired EmployeeSalaryComponentRepository employeeComponents;
    @Autowired AllowanceRunRepository allowanceRuns;
    @Autowired AllowanceTransactionRepository allowanceTransactions;

    @MockitoBean AccountsReportEmployees employees;
    @MockitoBean CatalogCache catalog;
    @MockitoBean EmployeeDisplayCache displayCache;
    @MockitoBean EmployeeClient employeeClient;
    @MockitoBean AttendanceClient attendance;

    private String fy;
    private long bankId;
    private long bilal;
    private long ali;

    @BeforeEach
    void seed() {
        fy = fiscalYears.findByStatus(FiscalYearStatus.CURRENT).orElseThrow().getCode();
        long base = 8_000_000L + (System.nanoTime() % 1_000_000L);
        bilal = base;
        ali = base + 1;

        Bank bank = new Bank();
        bank.setCode("T" + base);
        bank.setName("Test Bank " + base);
        bank.setBranch("Main Branch");
        bank.setAccountTitle("Forward Sports (Pvt) Ltd");
        bank.setAccountNumber("0000-" + base);
        bankId = banks.save(bank).getId();

        settings.save(payrollSettings(bilal, PaymentMode.BANK_TRANSFER));
        settings.save(payrollSettings(ali, PaymentMode.CASH));
        EmployeeBankDetails account = new EmployeeBankDetails();
        account.setEmployeeId(bilal);
        account.setBankId(bankId);
        account.setAccountTitle("Bilal Ahmed");
        account.setIban("PK00TEST" + base);
        account.setAccountNumber("ACC-" + base);
        bankDetails.save(account);

        // ---- Salary: posted August 2031 run ----
        PayrollRun run = new PayrollRun();
        run.setFiscalYearCode(fy);
        run.setPeriodYear(2031);
        run.setPeriodMonth(8);
        run.setStatus(PayrollRunStatus.POSTED);
        long runId = runs.findByPeriodYearAndPeriodMonth(2031, 8).map(PayrollRun::getId)
                .orElseGet(() -> runs.save(run).getId());
        Payslip p1 = payslips.save(payslip(runId, bilal, "20", "50000", "55000", "1200", "370", "53430"));
        Payslip p2 = payslips.save(payslip(runId, ali, "0101", "27000", "29000", "500", "370", "28130"));
        lines.saveAll(List.of(
                line(p1.getId(), "BASIC", ComponentType.EARNING, "50000"),
                line(p1.getId(), "SPECIAL", ComponentType.EARNING, "5000"),
                line(p2.getId(), "BASIC", ComponentType.EARNING, "27000"),
                line(p2.getId(), "MEAL", ComponentType.EARNING, "2000"),
                line(p2.getId(), "CANTEEN", ComponentType.DEDUCTION, "500")));

        // ---- Allowance run: meal 1,500 for Bilal ----
        SalaryComponent meal = components.findByCode("MEAL").orElseGet(() -> {
            SalaryComponent c = new SalaryComponent();
            c.setCode("MEAL");
            c.setName("Meal Allowance");
            c.setType(ComponentType.EARNING);
            c.setCalcType(CalcType.FLAT);
            c.setDisplayOrder(20);
            c.setActive(true);
            return components.save(c);
        });
        EmployeeSalaryComponent esc = new EmployeeSalaryComponent();
        esc.setEmployeeId(bilal);
        esc.setComponentId(meal.getId());
        esc.setAmount(new BigDecimal("1500"));
        esc.setEffectiveFrom(LocalDate.of(2031, 1, 1));
        esc = employeeComponents.save(esc);
        // One allowance run per period (uq_allowance_run_period) — reuse it across tests.
        AllowanceRun allowanceRun = allowanceRuns.findAll().stream()
                .filter(r -> r.getAllowanceYear() == 2031 && r.getAllowanceMonth() == 8).findFirst()
                .orElseGet(() -> {
                    AllowanceRun r = new AllowanceRun();
                    r.setFiscalYearCode(fy);
                    r.setAllowanceYear(2031);
                    r.setAllowanceMonth(8);
                    r.setStatus(AllowanceRunStatus.POSTED);
                    return allowanceRuns.save(r);
                });
        AllowanceTransaction at = new AllowanceTransaction();
        at.setAllowanceRunId(allowanceRun.getId());
        at.setEmployeeId(bilal);
        at.setEmployeeCode("20");
        at.setAllowanceYear(2031);
        at.setAllowanceMonth(8);
        at.setFiscalYearCode(fy);
        at.setGrade("G2");
        at.setEmployeeSalaryComponentId(esc.getId());
        at.setAmount(new BigDecimal("1500"));
        allowanceTransactions.save(at);

        // ---- Permanent advance: 10,000 on 10-Jan-2031, 1,000 recovered in Feb ----
        PermanentAdvance pa = new PermanentAdvance();
        pa.setRequestCode("PA-T" + (base % 100_000_000L));
        pa.setEmployeeId(bilal);
        pa.setAmount(new BigDecimal("10000"));
        pa.setMonthlyDeduction(new BigDecimal("1000"));
        pa.setReceivingDate(LocalDate.of(2031, 1, 10));
        pa.setStatus(AdvanceStatus.APPROVED);
        pa.setFiscalYearCode(fy);
        pa = permanentAdvances.save(pa);
        PermanentAdvanceDeduction d = new PermanentAdvanceDeduction();
        d.setEmployeeId(bilal);
        d.setSource(com.forward.hrms.payroll.enums.PermanentAdvanceDeductionSource.SALARY);
        d.setDeductedAmount(new BigDecimal("1000"));
        d.setRemainingBalance(new BigDecimal("9000"));
        d.setTransactionDate(LocalDate.of(2031, 2, 28));
        permanentDeductions.save(d);

        // ---- Temporary advance: approved batch, 3,000 each in Aug-2031 ----
        ShortTermAdvanceBatch batch = new ShortTermAdvanceBatch();
        batch.setRequestCode("STA-T" + (base % 100_000_000L));
        batch.setTargetYear(2031);
        batch.setTargetMonth(8);
        batch.setStatus(AdvanceStatus.APPROVED);
        batch.setTotalAmount(new BigDecimal("6000"));
        batch.setTotalEmployees(2);
        long batchId = batches.save(batch).getId();
        shortTermAdvances.saveAll(List.of(shortTerm(batchId, bilal), shortTerm(batchId, ali)));

        // ---- Bonus 4 of the current FY, computed ----
        BonusNoSettings bs = bonusSettings.findAll().stream()
                .filter(s -> s.getFiscalYearCode().equals(fy) && s.getBonusNo() == 4).findFirst()
                .orElseGet(() -> {
                    BonusNoSettings s = new BonusNoSettings();
                    s.setFiscalYearCode(fy);
                    s.setBonusNo(4);
                    s.setPercentageOfSalary(new BigDecimal("100"));
                    s.setStatus(BonusNoSettingsStatus.COMPUTED);
                    return bonusSettings.save(s);
                });
        bonusTransactions.saveAll(List.of(
                bonus(bilal, "20", BonusTransactionPaymentMode.BANK_TRANSFER, "27397", "0", "27397"),
                bonus(ali, "0101", BonusTransactionPaymentMode.CASH, "29750", "250", "30000")));

        // ---- employee-service stubs ----
        Map<Long, AccountsReportEmployee> emps = new HashMap<>();
        emps.put(bilal, employee(bilal, "20", "Bilal Ahmed", "Aslam Ahmed"));
        emps.put(ali, employee(ali, "0101", "Ali Raza", "Muhammad Raza"));
        when(employees.resolve(anyCollection())).thenAnswer(inv -> {
            Map<Long, AccountsReportEmployee> out = new HashMap<>();
            for (Object id : (java.util.Collection<?>) inv.getArgument(0)) {
                long l = (Long) id;
                out.put(l, emps.getOrDefault(l, AccountsReportEmployee.unknown(l)));
            }
            return out;
        });
        when(catalog.snapshot()).thenReturn(new CatalogCache.CatalogIndex(
                Map.of(1L, new DepartmentDto(1, "PROD", "Production", null, true, true)),
                Map.of(), Map.of(), Map.of(), Map.of()));
        when(attendance.shiftNameOn(anyLong(), any())).thenReturn("Morning A");
    }

    // ---------- registry & security ----------

    @Test
    void registry_listsEveryImplementedReport_andRequiresPermission() throws Exception {
        mvc.perform(get(BASE + "/registry").headers(caller("payroll.reports.run")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(36)))
                .andExpect(jsonPath("$[*].id", hasItems("A1", "A2", "A6", "B3", "B6", "C5", "C11", "E1", "E5",
                        "F-1", "F-6", "F-7")))
                .andExpect(jsonPath("$[?(@.id=='A2')].params[?(@.param=='MONTH')].required", hasItem(true)));

        mvc.perform(get(BASE + "/registry").headers(caller("payroll.read")))
                .andExpect(status().isForbidden());
    }

    // ---------- data: every family against the real schema ----------

    @Test
    void salaryRegister_bindsYearMonthAndReadsThePostedRun() throws Exception {
        mvc.perform(data("A2", salaryBody("\"empStatus\":\"ALL\"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows", hasSize(2)))
                .andExpect(jsonPath("$.rows[0].cardNo").value("20"))
                .andExpect(jsonPath("$.rows[0].srNo").value(1))
                .andExpect(jsonPath("$.rows[1].meal").value(2000.0))
                .andExpect(jsonPath("$.totals.netSalary").value(81560.0))
                .andExpect(jsonPath("$.appliedFilters", containsString("Month: August 2031")));

        mvc.perform(data("A7", salaryBody("\"empStatus\":\"ALL\"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows", hasSize(1)))
                .andExpect(jsonPath("$.groups[0].rows").value(1));

        mvc.perform(data("A4", salaryBody("\"empStatus\":\"ALL\"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].employeeCount").value(2));
    }

    @Test
    void advancesBonusAndAllowances_returnRows() throws Exception {
        mvc.perform(data("C2", "{\"empStatus\":\"ALL\",\"balance\":\"GREATER_THAN_ZERO\",\"cardList\":[\"20\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[?(@.cardNo=='20')].balance", hasItem(9000.0)));

        mvc.perform(data("C5", "{\"empStatus\":\"ALL\",\"cardList\":[\"20\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.header.employeeName").value("Bilal Ahmed"))
                .andExpect(jsonPath("$.rows", hasSize(2)))
                .andExpect(jsonPath("$.totals.runningBalance").value(9000.0));

        mvc.perform(data("B1", "{\"empStatus\":\"ALL\",\"dateFrom\":\"2031-08-01\",\"dateTo\":\"2031-08-31\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[?(@.cardNo=='20' || @.cardNo=='0101')]", hasSize(2)))
                .andExpect(jsonPath("$.rows[0].advanceMonth").value("2031-08"));

        mvc.perform(data("E2", "{\"fiscalYearCode\":\"" + fy + "\",\"bonusNo\":4,\"empStatus\":\"ALL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[?(@.cardNo=='0101')].payableAmount", hasItem(30000.0)));

        // Meal: Ali's payslip line (2,000) + Bilal's allowance run (1,500). allowance_transaction.amount
        // is a bigint column, so the JSON number may be integral — compare by value.
        MvcResult meal = mvc.perform(data("F-2", "{\"month\":\"2031-08\",\"empStatus\":\"ALL\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String body = meal.getResponse().getContentAsString();
        List<Number> bilalMeal = com.jayway.jsonpath.JsonPath.read(body, "$.rows[?(@.cardNo=='20')].amount");
        List<Number> aliMeal = com.jayway.jsonpath.JsonPath.read(body, "$.rows[?(@.cardNo=='0101')].amount");
        assertThat(bilalMeal).singleElement().satisfies(n -> assertThat(n.doubleValue()).isEqualTo(1500d));
        assertThat(aliMeal).singleElement().satisfies(n -> assertThat(n.doubleValue()).isEqualTo(2000d));

        // Allowances sheet: a MEAL column from the catalog, same amounts, Total = the row's components.
        MvcResult sheet = mvc.perform(data("F-7", "{\"month\":\"2031-08\",\"empStatus\":\"ALL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.columns[?(@.key=='MEAL')].label", hasItem("Meal Allowance")))
                .andExpect(jsonPath("$.columns[-1].key").value("signature"))
                .andReturn();
        String sheetBody = sheet.getResponse().getContentAsString();
        List<Number> bilalSheetMeal = com.jayway.jsonpath.JsonPath.read(sheetBody, "$.rows[?(@.cardNo=='20')].MEAL");
        assertThat(bilalSheetMeal).singleElement().satisfies(n -> assertThat(n.doubleValue()).isEqualTo(1500d));
        List<String> componentKeys = com.jayway.jsonpath.JsonPath.read(sheetBody,
                "$.columns[?(@.type=='AMOUNT' && @.key!='total')].key");
        List<java.util.Map<String, Object>> aliRow = com.jayway.jsonpath.JsonPath.read(sheetBody,
                "$.rows[?(@.cardNo=='0101')]");
        double sum = componentKeys.stream()
                .mapToDouble(k -> ((Number) aliRow.getFirst().get(k)).doubleValue()).sum();
        assertThat(((Number) aliRow.getFirst().get("total")).doubleValue()).isEqualTo(sum);

        // Grade Type = MANAGEMENT: the fixture employees carry no management grade.
        mvc.perform(data("F-7", "{\"month\":\"2031-08\",\"empStatus\":\"ALL\",\"gradeType\":\"MANAGEMENT\","
                        + "\"cardList\":[\"20\",\"0101\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows", hasSize(0)));
    }

    // ---------- errors ----------

    @Test
    void errors_areProblemDetails() throws Exception {
        mvc.perform(data("A2", "{\"fiscalYearCode\":\"" + fy + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ACCOUNTS_REPORT_FILTER_INVALID"))
                .andExpect(jsonPath("$.violations[*].field", hasItem("month")));

        mvc.perform(data("Z9", "{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNTS_REPORT_NOT_FOUND"));

        mvc.perform(post(BASE + "/A2/export?format=pdf").headers(caller("payroll.reports.run"))
                        .contentType(MediaType.APPLICATION_JSON).content(salaryBody("\"empStatus\":\"ALL\"")))
                .andExpect(status().isBadRequest());

        mvc.perform(post(BASE + "/A2/document").headers(caller("payroll.reports.run"))
                        .contentType(MediaType.APPLICATION_JSON).content(salaryBody("\"empStatus\":\"ALL\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ACCOUNTS_REPORT_NOT_A_DOCUMENT"));
    }

    // ---------- exports ----------

    @Test
    void export_xlsxAndCsv() throws Exception {
        MvcResult xlsx = mvc.perform(post(BASE + "/A2/export?format=xlsx").headers(caller("payroll.reports.run"))
                        .contentType(MediaType.APPLICATION_JSON).content(salaryBody("\"empStatus\":\"ALL\"")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("A2-salary-register-")))
                .andReturn();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx.getResponse().getContentAsByteArray()))) {
            assertThat(wb.getSheetAt(0).getRow(wb.getSheetAt(0).getLastRowNum()).getCell(0).getStringCellValue())
                    .isEqualTo("Total");
        }

        MvcResult csv = mvc.perform(post(BASE + "/C2/export?format=csv").headers(caller("payroll.reports.run"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"empStatus\":\"ALL\",\"balance\":\"GREATER_THAN_ZERO\",\"cardList\":[\"20\"]}"))
                .andExpect(status().isOk())
                .andReturn();
        String text = new String(csv.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(text).startsWith("﻿Sr No,Card No").contains("9000");
    }

    // ---------- PDF documents ----------

    @Test
    void documents_renderPdfs() throws Exception {
        pdf("A6", salaryBody("\"bankId\":" + bankId), "http-A6.pdf");
        pdf("B3", "{\"month\":\"2031-08\",\"empStatus\":\"ALL\"}", "http-B3.pdf");
        pdf("B6", "{\"month\":\"2031-08\",\"empStatus\":\"ALL\",\"bankId\":" + bankId + "}", "http-B6.pdf");
        pdf("E1", "{\"fiscalYearCode\":\"" + fy + "\",\"bonusNo\":4}", "http-E1.pdf");
        pdf("E5", "{\"fiscalYearCode\":\"" + fy + "\",\"bonusNo\":4,\"bankId\":" + bankId + "}", "http-E5.pdf");
        pdf("C5", "{\"empStatus\":\"ALL\",\"cardList\":[\"20\"]}", "http-C5.pdf");
    }

    @Test
    void salarySlips_englishAndUrdu() throws Exception {
        pdf("A1", salaryBody("\"empStatus\":\"ALL\""), "http-A1-en.pdf");
        MvcResult ur = mvc.perform(post(BASE + "/A1/document?language=ur").headers(caller("payroll.reports.run"))
                        .contentType(MediaType.APPLICATION_JSON).content(salaryBody("\"empStatus\":\"ALL\"")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("-ur.pdf")))
                .andReturn();
        save(ur.getResponse().getContentAsByteArray(), "http-A1-ur.pdf");
    }

    // ---------- helpers ----------

    private void pdf(String id, String body, String preview) throws Exception {
        MvcResult r = mvc.perform(post(BASE + "/" + id + "/document").headers(caller("payroll.reports.run"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("application/pdf")))
                .andReturn();
        byte[] bytes = r.getResponse().getContentAsByteArray();
        assertThat(new String(bytes, 0, 5, StandardCharsets.ISO_8859_1)).as(id).isEqualTo("%PDF-");
        save(bytes, preview);
    }

    private static void save(byte[] pdf, String name) throws Exception {
        Path dir = Path.of("target", "report-previews", "accounts");
        Files.createDirectories(dir);
        Files.write(dir.resolve(name), pdf);
    }

    private org.springframework.test.web.servlet.RequestBuilder data(String id, String body) {
        return post(BASE + "/" + id + "/data").headers(caller("payroll.reports.run"))
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    /**
     * August 2031 salary filter limited to this test's two cards: every test
     * seeds its own employees into the same run, and earlier tests' employees
     * resolve as unknown ("#id" cards), so the card list isolates the fixture.
     */
    private String salaryBody(String extra) {
        return "{\"fiscalYearCode\":\"" + fy + "\",\"month\":\"2031-08\",\"cardList\":[\"20\",\"0101\"]"
                + (extra.isEmpty() ? "" : "," + extra) + "}";
    }

    private static HttpHeaders caller(String... permissions) {
        HttpHeaders h = new HttpHeaders();
        h.add("X-User-Id", "990001");
        h.add("X-User-Name", "accounts.tester");
        h.add("X-User-Permissions", String.join(",", permissions));
        return h;
    }

    private static EmployeePayrollSettings payrollSettings(long employeeId, PaymentMode mode) {
        EmployeePayrollSettings s = new EmployeePayrollSettings();
        s.setEmployeeId(employeeId);
        s.setHireDate(LocalDate.of(2020, 1, 1));
        s.setEobiEnrolled(true);
        s.setPaymentMode(mode);
        return s;
    }

    private Payslip payslip(long runId, long employeeId, String code, String basic, String gross, String tax,
                            String eobi, String net) {
        Payslip p = new Payslip();
        p.setPayrollRunId(runId);
        p.setEmployeeId(employeeId);
        p.setEmployeeCode(code);
        p.setPeriodYear(2031);
        p.setPeriodMonth(8);
        p.setFiscalYearCode(fy);
        p.setBasic(new BigDecimal(basic));
        p.setGross(new BigDecimal(gross));
        p.setTax(new BigDecimal(tax));
        p.setEobi(new BigDecimal(eobi));
        p.setNet(new BigDecimal(net));
        p.setPaidDays(new BigDecimal("31"));
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

    private static ShortTermAdvance shortTerm(long batchId, long employeeId) {
        ShortTermAdvance s = new ShortTermAdvance();
        s.setBatchId(batchId);
        s.setEmployeeId(employeeId);
        s.setAmount(new BigDecimal("3000"));
        s.setTargetYear(2031);
        s.setTargetMonth(8);
        s.setStatus(ShortTermAdvanceStatus.APPROVED);
        return s;
    }

    private BonusTransaction bonus(long employeeId, String code, BonusTransactionPaymentMode mode, String due,
                                   String rfd, String net) {
        BonusTransaction t = new BonusTransaction();
        t.setFiscalYearCode(fy);
        t.setBonusNo(4);
        t.setEmployeeId(employeeId);
        t.setEmployeeCode(code);
        t.setDateOfJoining(LocalDate.of(2020, 1, 1));
        t.setBasicSalary(new BigDecimal("30000"));
        t.setPercentageOfSalary(new BigDecimal("100"));
        t.setPercentageSalaryAmount(new BigDecimal(due));
        t.setNoOfDays(365L);
        t.setRfdAdvanceAdded(new BigDecimal(rfd));
        t.setNetAmount(new BigDecimal(net));
        t.setBonusTransactionPaymentMode(mode);
        return t;
    }

    private static AccountsReportEmployee employee(long id, String card, String name, String father) {
        return new AccountsReportEmployee(id, card, name, father, "35202-1234567-1", LocalDate.of(2020, 1, 1),
                EmploymentStatus.ACTIVE, "PERMANENT", 1L, "Production", null, null, null, "Stitcher", "G2",
                null, true);
    }
}
