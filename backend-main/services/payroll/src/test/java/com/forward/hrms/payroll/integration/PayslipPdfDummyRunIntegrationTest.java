package com.forward.hrms.payroll.integration;

import com.forward.hrms.commons.cache.EmployeeDisplay;
import com.forward.hrms.commons.cache.EmployeeDisplayCache;
import com.forward.hrms.commons.storage.ObjectStorage;
import com.forward.hrms.commons.testsupport.containers.WithTestcontainers;
import com.forward.hrms.payroll.client.AttendanceClient;
import com.forward.hrms.payroll.client.FairPriceShopClient;
import com.forward.hrms.payroll.client.EmployeeClient;
import com.forward.hrms.payroll.client.dto.attendace.AnnualLeaveSummaryDto;
import com.forward.hrms.payroll.client.dto.attendace.EmployeeLeavePayslipSummaryDto;
import com.forward.hrms.payroll.client.dto.attendace.LeaveTypeAvailedDto;
import com.forward.hrms.payroll.entity.EmployeePayrollSettings;
import com.forward.hrms.payroll.entity.EmployeeSalary;
import com.forward.hrms.payroll.entity.EmployeeSalaryComponent;
import com.forward.hrms.payroll.entity.FiscalYear;
import com.forward.hrms.payroll.entity.PayrollConfig;
import com.forward.hrms.payroll.entity.PayrollRun;
import com.forward.hrms.payroll.entity.Payslip;
import com.forward.hrms.payroll.entity.SalaryComponent;
import com.forward.hrms.payroll.enums.PaymentMode;
import com.forward.hrms.payroll.enums.PayrollRunStatus;
import com.forward.hrms.payroll.repository.AllowanceTransactionRepository;
import com.forward.hrms.payroll.repository.EmployeeBankDetailsRepository;
import com.forward.hrms.payroll.repository.EmployeePayrollSettingsRepository;
import com.forward.hrms.payroll.repository.EmployeeSalaryComponentRepository;
import com.forward.hrms.payroll.repository.EmployeeSalaryRepository;
import com.forward.hrms.payroll.repository.FiscalYearRepository;
import com.forward.hrms.payroll.repository.PayrollConfigRepository;
import com.forward.hrms.payroll.repository.PayrollRunRepository;
import com.forward.hrms.payroll.repository.PayrollRunSkipRepository;
import com.forward.hrms.payroll.repository.PayslipLineRepository;
import com.forward.hrms.payroll.repository.PayslipRepository;
import com.forward.hrms.payroll.repository.SalaryComponentRepository;
import com.forward.hrms.payroll.service.AttendanceSnapshot;
import com.forward.hrms.payroll.service.PayrollComputeService;
import com.forward.hrms.payroll.service.PayslipPdfService;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Dummy-entry smoke test for the payslip PDF: one employee with allowance,
 * tax, EOBI and leave usage is computed into a run, then both PDF paths
 * (post-time {@code generateAll}, on-demand {@code renderRun}) are rendered
 * and their text is checked against the run-detail data. The PDFs are
 * written to {@code target/} for eyeballing.
 */
@SpringBootTest
@WithTestcontainers
class PayslipPdfDummyRunIntegrationTest {

    private static final long EMP = 77_001L;

    @Autowired PayrollComputeService computeService;
    @Autowired PayslipPdfService pdfService;
    @Autowired PayrollRunRepository runRepo;
    @Autowired PayslipRepository payslipRepo;
    @Autowired PayslipLineRepository lineRepo;
    @Autowired PayrollRunSkipRepository skipRepo;
    @Autowired EmployeeBankDetailsRepository bankRepo;
    @Autowired AllowanceTransactionRepository allowanceTransactionRepo;
    @Autowired EmployeeSalaryComponentRepository componentLineRepo;
    @Autowired EmployeeSalaryRepository salaryRepo;
    @Autowired EmployeePayrollSettingsRepository settingsRepo;
    @Autowired SalaryComponentRepository catalogRepo;
    @Autowired PayrollConfigRepository statRepo;
    @Autowired FiscalYearRepository fyRepo;
    @Autowired com.forward.hrms.commons.saga.SagaRepository sagaRepo;

    @MockitoBean ObjectStorage storage;
    @MockitoBean EmployeeClient employeeClient;
    @MockitoBean AttendanceClient attendanceClient;
    @MockitoBean FairPriceShopClient fairPriceShopClient;
    @MockitoBean EmployeeDisplayCache displayCache;

    private YearMonth period;
    private PayrollRun run;

    @BeforeEach
    void seedDummyRun() {
        sagaRepo.deleteAll();
        lineRepo.deleteAll();
        payslipRepo.deleteAll();
        skipRepo.deleteAll();
        bankRepo.deleteAll();
        runRepo.deleteAll();
        allowanceTransactionRepo.deleteAll();
        componentLineRepo.deleteAll();
        salaryRepo.deleteAll();
        settingsRepo.deleteAll();
        statRepo.deleteAll();

        period = YearMonth.now();
        LocalDate start = period.atDay(1);

        PayrollConfig stat = new PayrollConfig();
        stat.setEffectiveFrom(start.minusYears(1));
        stat.setEobiAmount(new BigDecimal("1500.00"));
        stat.setPessiRatePercent(new BigDecimal("6.00"));
        stat.setPessiSalaryCap(new BigDecimal("25000.00"));
        stat.setCanteenPerDayRateManagement(BigDecimal.ZERO);
        stat.setCanteenPerDayRateOther(BigDecimal.ZERO);
        statRepo.save(stat);

        EmployeePayrollSettings s = new EmployeePayrollSettings();
        s.setEmployeeId(EMP);
        s.setHireDate(LocalDate.of(2021, 3, 15));
        s.setEobiEnrolled(true);
        s.setPaymentMode(PaymentMode.CASH);
        settingsRepo.save(s);

        EmployeeSalary sal = new EmployeeSalary();
        sal.setEmployeeId(EMP);
        sal.setBasic(new BigDecimal("100000.00"));
        sal.setEffectiveFrom(start.minusMonths(1));
        salaryRepo.save(sal);
        component("HRA", "10000.00", start);
        component("INCOME_TAX", "5000.00", start);

        int days = period.lengthOfMonth();
        when(attendanceClient.snapshot(anyLong(), any(), any(), any())).thenReturn(new AttendanceSnapshot(
                BigDecimal.ZERO, BigDecimal.valueOf(days), BigDecimal.ZERO,
                days, BigDecimal.valueOf(days), BigDecimal.ZERO, 0, 0));
        when(attendanceClient.payslipSummaries(anyCollection(), any(), any(), any())).thenReturn(Map.of(EMP,
                new EmployeeLeavePayslipSummaryDto(EMP, List.of(
                        new LeaveTypeAvailedDto("CASUAL", "Casual Leave", new BigDecimal("2"), new BigDecimal("5")),
                        new LeaveTypeAvailedDto("MEDICAL_M1", "Medical Leave", new BigDecimal("1"), new BigDecimal("3"))),
                        new AnnualLeaveSummaryDto(new BigDecimal("14"), new BigDecimal("4"),
                                BigDecimal.ZERO, new BigDecimal("10")),
                        null)));
        EmployeeClient.EmployeeBrief brief = new EmployeeClient.EmployeeBrief(EMP, "13411", "Ali Raza",
                null, null, null, null, null, null, LocalDate.of(2021, 3, 15), "Muhammad Raza");
        when(employeeClient.getMany(anyCollection(), any())).thenReturn(Map.of(EMP, brief));
        when(employeeClient.getMany(anyCollection())).thenReturn(Map.of(EMP, brief));
        when(displayCache.getMany(anyCollection())).thenReturn(Map.of(EMP, new EmployeeDisplay(EMP, "13411",
                "Ali Raza", 1L, "Stitching", null, null, 2L, "Supervisor", null)));

        FiscalYear fy = fyRepo.findAll().stream().findFirst().orElseThrow();
        PayrollRun r = new PayrollRun();
        r.setFiscalYearCode(fy.getCode());
        r.setPeriodYear(period.getYear());
        r.setPeriodMonth(period.getMonthValue());
        r.setStatus(PayrollRunStatus.LOCKED);
        run = runRepo.save(r);
        computeService.computeRun(run);
        run = runRepo.findById(run.getId()).orElseThrow();
    }

    @Test
    void generateAll_rendersRunDetailDataAndStoresKey() throws Exception {
        pdfService.generateAll(run);

        ArgumentCaptor<InputStream> body = ArgumentCaptor.forClass(InputStream.class);
        verify(storage).upload(anyString(), eq("payslips/%d/%02d/13411.pdf"
                .formatted(period.getYear(), period.getMonthValue())), body.capture(), anyLong(),
                eq("application/pdf"), any());
        byte[] pdf = body.getValue().readAllBytes();
        Files.write(Path.of("target", "payslip-dummy-13411.pdf"), pdf);

        Payslip slip = payslipRepo.findAll().stream()
                .filter(p -> Objects.equals(p.getPayrollRunId(), run.getId())).findFirst().orElseThrow();
        assertThat(slip.getPdfObjectKey()).endsWith("/13411.pdf");
        assertThat(slip.getGeneratedAt()).isNotNull();

        assertRunDetailFields(text(pdf, 1));
    }

    @Test
    void renderRun_isOnePdfOfTheRunWithTheSameData() throws Exception {
        byte[] pdf = pdfService.renderRun(run.getId());
        Files.write(Path.of("target", "payslips-dummy-run.pdf"), pdf);

        assertThat(new PdfReader(pdf).getNumberOfPages()).isEqualTo(1);
        assertRunDetailFields(text(pdf, 1));
    }

    private void assertRunDetailFields(String text) {
        assertThat(text).contains("Forward Sports (Pvt) Ltd.");
        assertThat(text).contains(period.getMonth().getDisplayName(java.time.format.TextStyle.FULL,
                java.util.Locale.ENGLISH) + " " + period.getYear());
        assertThat(text).contains("13411", "Ali Raza", "Muhammad Raza", "Stitching", "Supervisor", "Mar 15, 2021");
        assertThat(text).contains("100,000.00");               // basic
        assertThat(text).contains("10,000.00");                // HRA line
        assertThat(text).contains("110,000.00");               // gross
        assertThat(text).contains("6,500.00");                 // tax 5,000 + EOBI 1,500
        assertThat(text).contains("103,500.00");               // net
        assertThat(text).contains("Rupees One Lakh Three Thousand Five Hundred Only");
        assertThat(text).contains("ANNUAL · BALANCE", "Fiscal Year");
    }

    private void component(String code, String amount, LocalDate start) {
        SalaryComponent catalog = catalogRepo.findByCode(code).orElseThrow();
        if (!catalog.isCalculateWithSalary()) {
            catalog.setCalculateWithSalary(true);
            catalogRepo.save(catalog);
        }
        EmployeeSalaryComponent line = new EmployeeSalaryComponent();
        line.setEmployeeId(EMP);
        line.setComponentId(catalog.getId());
        line.setAmount(new BigDecimal(amount));
        line.setEffectiveFrom(start.minusMonths(1));
        componentLineRepo.save(line);
    }

    private static String text(byte[] pdf, int page) throws Exception {
        return new PdfTextExtractor(new PdfReader(pdf)).getTextFromPage(page);
    }
}
