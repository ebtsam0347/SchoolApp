package com.forward.hrms.payroll.service;

import com.forward.hrms.commons.cache.CatalogCache;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.core.error.UnprocessableError;
import com.forward.hrms.commons.core.error.ValidationError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.reports.JasperRenderer;
import com.forward.hrms.employee.enums.EmploymentStatus;
import com.forward.hrms.payroll.client.AttendanceClient;
import com.forward.hrms.payroll.dto.request.AccountsReportFilter;
import com.forward.hrms.payroll.dto.response.composite.PayrollResponses.PayslipLineItem;
import com.forward.hrms.payroll.entity.Bank;
import com.forward.hrms.payroll.entity.BonusTransaction;
import com.forward.hrms.payroll.entity.EmployeeBankDetails;
import com.forward.hrms.payroll.entity.EmployeePayrollSettings;
import com.forward.hrms.payroll.entity.Payslip;
import com.forward.hrms.payroll.entity.PermanentAdvance;
import com.forward.hrms.payroll.entity.PermanentAdvanceDeduction;
import com.forward.hrms.payroll.entity.SalaryComponent;
import com.forward.hrms.payroll.entity.ShortTermAdvance;
import com.forward.hrms.payroll.enums.AdvanceStatus;
import com.forward.hrms.payroll.enums.BonusTransactionPaymentMode;
import com.forward.hrms.payroll.enums.ComponentType;
import com.forward.hrms.payroll.enums.EmployeeStatusFilter;
import com.forward.hrms.payroll.enums.PaymentMode;
import com.forward.hrms.payroll.enums.ShortTermAdvanceStatus;
import com.forward.hrms.payroll.repository.AllowanceReportRepository;
import com.forward.hrms.payroll.repository.SalaryComponentRepository;
import com.forward.hrms.payroll.repository.BankRepository;
import com.forward.hrms.payroll.repository.BonusReportRepository;
import com.forward.hrms.payroll.repository.EmployeeBankDetailsRepository;
import com.forward.hrms.payroll.repository.EmployeePayrollSettingsRepository;
import com.forward.hrms.payroll.repository.PayslipLineRepository;
import com.forward.hrms.payroll.repository.PermanentAdvanceDeductionRepository;
import com.forward.hrms.payroll.repository.PermanentAdvanceReportRepository;
import com.forward.hrms.payroll.repository.PermanentAdvanceRepository;
import com.forward.hrms.payroll.repository.SalaryReportRepository;
import com.forward.hrms.payroll.repository.ShortTermAdvanceReportRepository;
import com.forward.hrms.payroll.repository.ShortTermAdvanceRepository;
import com.forward.hrms.payroll.repository.projection.EmployeeCodeAmountProjection;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;

import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PDF documents of the accounts reports through the real Jasper templates
 * (letters, slips, ledger, Urdu payslip) with mocked data. Every PDF lands
 * in {@code target/report-previews/accounts/} for a visual check.
 *
 * <p>Fixture: card 20 (Bilal, father Aslam) — bank transfer at HBL,
 * account HBL-20; card 0101 (Ali) — cash. August 2026 salary, a 3,000
 * temporary advance for each in Aug-2026, FY 2026-27 bonus 1, and a
 * permanent advance ledger for card 20.
 */
class AccountsDocumentsTest {

    private final SalaryReportRepository salaryRepo = mock(SalaryReportRepository.class);
    private final ShortTermAdvanceReportRepository shortTermRepo = mock(ShortTermAdvanceReportRepository.class);
    private final BonusReportRepository bonusRepo = mock(BonusReportRepository.class);
    private final PermanentAdvanceReportRepository permanentRepo = mock(PermanentAdvanceReportRepository.class);
    private final EmployeePayrollSettingsRepository settingsRepo = mock(EmployeePayrollSettingsRepository.class);
    private final EmployeeBankDetailsRepository bankDetailsRepo = mock(EmployeeBankDetailsRepository.class);
    private final BankRepository bankRepo = mock(BankRepository.class);
    private final PayslipLineRepository lineRepo = mock(PayslipLineRepository.class);
    private final AccountsReportEmployees employees = mock(AccountsReportEmployees.class);
    private final PayslipPdfService payslipPdf = mock(PayslipPdfService.class);
    private final AttendanceClient attendance = mock(AttendanceClient.class);
    private final AllowanceReportRepository allowanceRepo = mock(AllowanceReportRepository.class);
    private final SalaryComponentRepository componentRepo = mock(SalaryComponentRepository.class);

    private final Bank hbl = new Bank();
    private AccountsDocumentService documents;

    @BeforeEach
    void setUp() {
        List<AccountsReportSource> sources = List.of(
                new SalaryReportSource(salaryRepo, lineRepo, settingsRepo, bankDetailsRepo, bankRepo,
                        mock(ShortTermAdvanceRepository.class), mock(PermanentAdvanceRepository.class),
                        mock(PermanentAdvanceDeductionRepository.class)),
                new TemporaryAdvanceReportSource(shortTermRepo, settingsRepo, bankDetailsRepo, bankRepo),
                new BonusReportSource(bonusRepo, bankDetailsRepo, bankRepo),
                new PermanentAdvanceReportSource(permanentRepo),
                new AllowanceReportSource(allowanceRepo, componentRepo));
        AccountsReportService reports = new AccountsReportService(sources, employees, mock(CatalogCache.class),
                mock(AuditEmitter.class));
        documents = new AccountsDocumentService(reports, employees, bankRepo, salaryRepo, bonusRepo, payslipPdf,
                attendance, new JasperRenderer(), "Muhammad Tariq", "Director");

        hbl.setId(7L);
        hbl.setCode("HBL");
        hbl.setName("Habib Bank Limited");
        hbl.setBranch("Paris Road Branch, Sialkot");
        hbl.setAddress("Paris Road, Sialkot");
        hbl.setAccountTitle("Forward Sports (Pvt) Ltd");
        hbl.setAccountNumber("0123-7900-1111-03");
        when(bankRepo.findById(7L)).thenReturn(Optional.of(hbl));
        when(bankRepo.findAllByOrderByCodeAsc()).thenReturn(List.of(hbl));

        when(settingsRepo.findAllByEmployeeIdIn(anyCollection())).thenReturn(List.of(
                settings(101, PaymentMode.CASH), settings(102, PaymentMode.BANK_TRANSFER)));
        EmployeeBankDetails account = new EmployeeBankDetails();
        account.setEmployeeId(102L);
        account.setBankId(7L);
        account.setAccountNumber("HBL-20");
        account.setIban("PK00HBL20");
        when(bankDetailsRepo.findByEmployeeIdIn(anyCollection())).thenReturn(List.of(account));

        when(salaryRepo.findComputedPayslips(2026, 8)).thenReturn(List.of(
                payslip(1, 101, "29930"), payslip(2, 102, "52430.50")));
        when(shortTermRepo.findPaidBetween(anyInt(), anyInt())).thenReturn(List.of(
                advance(101, "3000"), advance(102, "3000")));
        when(bonusRepo.findComputed("2026-27", 1)).thenReturn(List.of(
                bonus(101, BonusTransactionPaymentMode.CASH, "29750", "250", "30000"),
                bonus(102, BonusTransactionPaymentMode.BANK_TRANSFER, "27397", "0", "25397")));
        when(permanentRepo.findPaid()).thenReturn(List.of(permanentAdvance()));
        when(permanentRepo.findAllDeductions()).thenReturn(List.of(
                deduction(11, LocalDate.of(2026, 2, 28), "1000"), deduction(12, LocalDate.of(2026, 3, 31), "1000")));
        when(attendance.shiftNameOn(anyLong(), any())).thenReturn("Morning A");

        Map<Long, AccountsReportEmployee> emps = new HashMap<>();
        emps.put(101L, employee(101, "0101", "Ali Raza", "Muhammad Raza"));
        emps.put(102L, employee(102, "20", "Bilal Ahmed", "Aslam Ahmed"));
        when(employees.resolve(anyCollection())).thenReturn(emps);
    }

    // ---------- bank letters ----------

    @Test
    void salaryBankLetter_onlyTransferEmployeesOfThatBank() throws Exception {
        byte[] pdf = render("A6", filter(f -> {
            f.fiscalYearCode = "2026-27";
            f.month = YearMonth.of(2026, 8);
            f.bankId = 7L;
        }));
        String text = text(pdf, "A6-salary-bank-letter.pdf");
        assertThat(text).contains("SALARY BANK TRANSFER LETTER")
                .contains("Habib Bank Limited").contains("Salary Transfer for the Month of August 2026")
                .contains("0123-7900-1111-03").contains("Bilal Ahmed").contains("Aslam Ahmed").contains("HBL-20")
                .contains("52,430.50").contains("Fifty Two Thousand Four Hundred Thirty")
                .contains("Muhammad Tariq").contains("Director")
                .doesNotContain("Ali Raza").doesNotContain("Ref:");
    }

    @Test
    void temporaryAdvanceAndBonusLetters_useTheirOwnWording() throws Exception {
        String b6 = text(render("B6", filter(f -> {
            f.month = YearMonth.of(2026, 8);
            f.bankId = 7L;
            f.status = EmployeeStatusFilter.ALL;
        })), "B6-temporary-advance-bank-letter.pdf");
        assertThat(b6).contains("TEMPORARY ADVANCE BANK TRANSFER LETTER")
                .contains("temporary advance for the month of August 2026").contains("Bilal Ahmed")
                .contains("3,000.00").doesNotContain("Ali Raza");

        String e5 = text(render("E5", filter(f -> {
            f.fiscalYearCode = "2026-27";
            f.bonusNo = 1;
            f.bankId = 7L;
        })), "E5-bonus-bank-letter.pdf");
        assertThat(e5).contains("BONUS BANK TRANSFER LETTER").contains("Bonus No. 1 for the Financial Year 2026-27")
                .contains("25,397.00").doesNotContain("Ali Raza");
    }

    @Test
    void bankLetter_refusesABankWithoutCompanyAccount() {
        hbl.setAccountNumber(null);
        hbl.setIban(null);
        assertThatThrownBy(() -> render("A6", filter(f -> {
            f.fiscalYearCode = "2026-27";
            f.month = YearMonth.of(2026, 8);
            f.bankId = 7L;
        }))).isInstanceOf(UnprocessableError.class);
    }

    // ---------- slips ----------

    @Test
    void temporaryAdvanceSlips_twoPerPage() throws Exception {
        byte[] pdf = render("B3", filter(f -> {
            f.month = YearMonth.of(2026, 8);
            f.status = EmployeeStatusFilter.ALL;
        }));
        PdfReader reader = new PdfReader(pdf);
        assertThat(reader.getNumberOfPages()).isEqualTo(1);
        String text = text(pdf, "B3-temporary-advance-slips.pdf");
        assertThat(text).contains("TEMPORARY ADVANCE SLIP").contains("Ali Raza").contains("Bilal Ahmed")
                .contains("Morning A").contains("August 2026").contains("3,000.00")
                .contains("Rupees Three Thousand Only");
    }

    @Test
    void bonusSlips_showDueRfdAndNet() throws Exception {
        byte[] pdf = render("E1", filter(f -> {
            f.fiscalYearCode = "2026-27";
            f.bonusNo = 1;
        }));
        String text = text(pdf, "E1-bonus-slips.pdf");
        assertThat(text).contains("BONUS SLIP - BONUS NO. 1 - FY 2026-27").contains("29,750.00")
                .contains("250.00").contains("30,000.00").contains("Rupees Thirty Thousand Only")
                .contains("Bilal Ahmed");
    }

    @Test
    void salarySlips_delegateToThePayslipRenderer_inFilterOrderAndLanguage() {
        when(payslipPdf.renderPayslips(eq(900L), any(), eq(true))).thenReturn(new byte[] {1});
        AccountsReportFilter f = filter(x -> {
            x.fiscalYearCode = "2026-27";
            x.month = YearMonth.of(2026, 8);
        });
        AccountsReportService.Exported out = documents.render("A1", f, AccountsDocumentService.Language.UR);
        verify(payslipPdf).renderPayslips(900L, List.of(102L, 101L), true);
        assertThat(out.filename()).endsWith("-ur.pdf");
    }

    // ---------- ledger ----------

    @Test
    void ledger_onePageWithRunningBalance() throws Exception {
        byte[] pdf = render("C5", filter(f -> {
            f.cardList = List.of("20");
            f.status = EmployeeStatusFilter.ALL;
        }));
        String text = text(pdf, "C5-permanent-advance-ledger.pdf");
        assertThat(text).contains("PERMANENT ADVANCE LEDGER").contains("Bilal Ahmed").contains("Aslam Ahmed")
                .contains("10,000.00").contains("9,000.00").contains("8,000.00");
    }

    // ---------- F-7 allowances calculation sheet ----------

    /** The legacy sheet's eight payables: Travel … Tax Relief, plus Meal and Seniority. */
    private static final List<String> SHEET_CODES = List.of("TRAVEL", "EDUCATION", "MOBILE", "SPECIAL",
            "ACCOMMODATION", "TAX_RELIEF", "MEAL", "SENIORITY");

    @Test
    void allowancesSheet_oneColumnPerComponent_withTotals() throws Exception {
        sheetComponents(SHEET_CODES);
        when(allowanceRepo.sumPayslipLinesByCode(eq(2026), eq(8), anyCollection())).thenReturn(List.of());
        when(allowanceRepo.sumAllowanceRunByCode(eq(2026), eq(8), anyCollection())).thenReturn(List.of(
                codeAmount(102, "TRAVEL", "45000"), codeAmount(102, "EDUCATION", "55000"),
                codeAmount(102, "TAX_RELIEF", "97000"),
                codeAmount(101, "TRAVEL", "38000"), codeAmount(101, "EDUCATION", "37000"),
                codeAmount(101, "SPECIAL", "4000"), codeAmount(101, "TAX_RELIEF", "30500.50")));

        byte[] pdf = render("F-7", filter(f -> {
            f.month = YearMonth.of(2026, 8);
            f.status = EmployeeStatusFilter.ALL;
        }));
        String text = text(pdf, "F-7-allowances-calculation.pdf");
        assertThat(text).contains("ALLOWANCES CALCULATION").contains("August 2026")
                .contains("Basic Information").contains("Payables").contains("Signature")
                .contains("Tax Relief").contains("Accommodation")
                .contains("Bilal Ahmed").contains("Ali Raza")
                .contains("197,000")                       // card 20: 45,000 + 55,000 + 97,000
                .contains("109,500.50")                    // card 0101, sub-rupee tax relief keeps decimals
                .contains("306,500.50")                    // grand total
                .contains("TOTAL").contains("2 employees")
                .contains("Prepared By").contains("Approved By");
    }

    @Test
    void allowancesSheet_manyComponents_stillFitsTheLegalPage() throws Exception {
        List<String> codes = new ArrayList<>(SHEET_CODES);
        codes.addAll(List.of("CONVEYANCE", "MEDICAL", "HRA", "UTILITY"));
        sheetComponents(codes);
        when(allowanceRepo.sumPayslipLinesByCode(eq(2026), eq(8), anyCollection())).thenReturn(List.of());
        when(allowanceRepo.sumAllowanceRunByCode(eq(2026), eq(8), anyCollection())).thenReturn(List.of(
                codeAmount(102, "UTILITY", "1250000"), codeAmount(101, "MEDICAL", "3000")));

        byte[] pdf = render("F-7", filter(f -> {
            f.month = YearMonth.of(2026, 8);
            f.status = EmployeeStatusFilter.ALL;
        }));
        PdfReader reader = new PdfReader(pdf);
        // Legal landscape: 14 x 8.5 in.
        assertThat(reader.getPageSizeWithRotation(1).getWidth()).isEqualTo(1008f);
        assertThat(reader.getPageSizeWithRotation(1).getHeight()).isEqualTo(612f);
        assertThat(text(pdf, "F-7-allowances-calculation-12-components.pdf"))
                .contains("1,250,000").contains("1,253,000");
    }

    private void sheetComponents(List<String> codes) {
        List<SalaryComponent> list = new ArrayList<>();
        for (String code : codes) {
            SalaryComponent c = new SalaryComponent();
            c.setCode(code);
            String words = code.charAt(0) + code.substring(1).toLowerCase().replace('_', ' ');
            if (code.equals("TAX_RELIEF")) words = "Tax Relief";
            c.setName(words);
            c.setType(ComponentType.EARNING);
            list.add(c);
        }
        when(componentRepo.filter(ComponentType.EARNING, true)).thenReturn(list);
    }

    private static EmployeeCodeAmountProjection codeAmount(long employeeId, String code, String amount) {
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

    // ---------- guards ----------

    @Test
    void listReportsAndEmptyResultsAreRefused() {
        assertThatThrownBy(() -> render("A2", filter(f -> {
            f.fiscalYearCode = "2026-27";
            f.month = YearMonth.of(2026, 8);
        }))).isInstanceOf(ValidationError.class);
        assertThatThrownBy(() -> render("B3", filter(f -> {
            f.month = YearMonth.of(2026, 8);
            f.status = EmployeeStatusFilter.NON_ACTIVE;
        }))).isInstanceOf(NotFoundError.class);
    }

    // ---------- Urdu payslip template ----------

    @Test
    void urduPayslipTemplate_rendersNastaliq() throws Exception {
        Map<String, Object> p = new HashMap<>();
        p.put("companyName", "Forward Sports (Pvt) Ltd.");
        p.put("companyAddress", "Sialkot, Punjab, Pakistan");
        p.put("periodText", "August 2026");
        p.put("fiscalYear", "2026-27");
        p.put("heldBannerUr", "");
        p.put("employeeCode", "20");
        p.put("employeeName", "Bilal Ahmed");
        p.put("fatherName", "Aslam Ahmed");
        p.put("designation", "Stitcher");
        p.put("department", "Production");
        p.put("grade", "G2");
        p.put("dateOfJoining", "01-01-2020");
        p.put("bankName", "Habib Bank Limited");
        p.put("bankAccount", "HBL-20");
        p.put("advanceBalanceText", "8,000.00");
        for (String k : List.of("workingDaysText", "daysWorkedText", "presentDaysText", "absentDaysText",
                "lopDaysText", "paidLeaveDaysText", "unpaidLeaveDaysText", "holidaysText", "weeklyOffText",
                "overtimeHoursText", "casualLeavesMonthText", "totalCasualLeavesText", "medicalLeavesMonthText",
                "totalMedicalLeavesText", "annualAvailedText", "annualBalanceText")) {
            p.put(k, "2");
        }
        p.put("grossSalaryText", "55,000.00");
        p.put("totalDeductionText", "2,570.00");
        p.put("netSalaryText", "52,430.00");
        p.put("netSalaryWords", AmountInWords.of(new BigDecimal("52430")));
        p.put("generatedAt", "29-09-2026 10:00");
        p.put("generatedBy", "accounts");

        List<Map<String, ?>> rows = new ArrayList<>();
        rows.add(line(new PayslipLineItem("BASIC", "Basic", new BigDecimal("50000")),
                new PayslipLineItem("INCOME_TAX", "Income Tax", new BigDecimal("1200"))));
        rows.add(line(new PayslipLineItem("SPECIAL", "Special Allowance", new BigDecimal("5000")),
                new PayslipLineItem("REWARD_X", "Reward Policy", new BigDecimal("1370"))));

        byte[] pdf = new JasperRenderer().renderPdf("/jasper/payslip-ur.jrxml", p, new JRMapCollectionDataSource(rows));
        save(pdf, "A1-urdu-payslip.pdf");
        String text = new PdfTextExtractor(new PdfReader(pdf)).getTextFromPage(1);
        assertThat(text).contains("Bilal Ahmed").contains("52,430.00").contains("Reward Policy");
        assertThat(PayslipPdfService.urduName(new PayslipLineItem("BASIC", "Basic", BigDecimal.ONE)))
                .isEqualTo("بنیادی تنخواہ");
        assertThat(PayslipPdfService.urduName(new PayslipLineItem("REWARD_X", "Reward Policy", BigDecimal.ONE)))
                .isNull();
    }

    private static Map<String, Object> line(PayslipLineItem earning, PayslipLineItem deduction) {
        Map<String, Object> r = new HashMap<>();
        r.put("earningNameUr", PayslipPdfService.urduName(earning));
        r.put("earningNameEn", PayslipPdfService.urduName(earning) == null ? earning.name() : null);
        r.put("earningAmountText", PayslipPdfService.fmt(earning.amount()));
        r.put("deductionNameUr", PayslipPdfService.urduName(deduction));
        r.put("deductionNameEn", PayslipPdfService.urduName(deduction) == null ? deduction.name() : null);
        r.put("deductionAmountText", PayslipPdfService.fmt(deduction.amount()));
        return r;
    }

    // ---------- helpers ----------

    private byte[] render(String id, AccountsReportFilter f) {
        return documents.render(id, f, AccountsDocumentService.Language.EN).body();
    }

    private static String text(byte[] pdf, String previewName) throws Exception {
        save(pdf, previewName);
        PdfReader reader = new PdfReader(pdf);
        PdfTextExtractor extractor = new PdfTextExtractor(reader);
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= reader.getNumberOfPages(); i++) sb.append(extractor.getTextFromPage(i)).append('\n');
        return sb.toString();
    }

    private static void save(byte[] pdf, String name) throws Exception {
        Path dir = Path.of("target", "report-previews", "accounts");
        Files.createDirectories(dir);
        Files.write(dir.resolve(name), pdf);
    }

    private static Payslip payslip(long id, long employeeId, String net) {
        Payslip p = new Payslip();
        p.setId(id);
        p.setPayrollRunId(900L);
        p.setEmployeeId(employeeId);
        p.setPeriodYear(2026);
        p.setPeriodMonth(8);
        p.setFiscalYearCode("2026-27");
        p.setNet(new BigDecimal(net));
        return p;
    }

    private static ShortTermAdvance advance(long employeeId, String amount) {
        ShortTermAdvance s = new ShortTermAdvance();
        s.setId(employeeId);
        s.setEmployeeId(employeeId);
        s.setTargetYear(2026);
        s.setTargetMonth(8);
        s.setAmount(new BigDecimal(amount));
        s.setStatus(ShortTermAdvanceStatus.APPROVED);
        return s;
    }

    private static BonusTransaction bonus(long employeeId, BonusTransactionPaymentMode mode, String due, String rfd,
                                          String net) {
        BonusTransaction t = new BonusTransaction();
        t.setEmployeeId(employeeId);
        t.setFiscalYearCode("2026-27");
        t.setBonusNo(1);
        t.setBonusTransactionPaymentMode(mode);
        t.setDateOfJoining(LocalDate.of(2020, 1, 1));
        t.setBasicSalary(new BigDecimal("30000"));
        t.setNoOfMonths(80);
        t.setNoOfDays(365L);
        t.setPercentageSalaryAmount(new BigDecimal(due));
        t.setRfdAdvanceAdded(new BigDecimal(rfd));
        t.setNetAmount(new BigDecimal(net));
        return t;
    }

    private static PermanentAdvance permanentAdvance() {
        PermanentAdvance a = new PermanentAdvance();
        a.setId(1L);
        a.setRequestCode("PA-1");
        a.setEmployeeId(102L);
        a.setAmount(new BigDecimal("10000"));
        a.setMonthlyDeduction(new BigDecimal("1000"));
        a.setReceivingDate(LocalDate.of(2026, 1, 10));
        a.setStatus(AdvanceStatus.APPROVED);
        return a;
    }

    private static PermanentAdvanceDeduction deduction(long id, LocalDate date, String amount) {
        PermanentAdvanceDeduction d = new PermanentAdvanceDeduction();
        d.setId(id);
        d.setEmployeeId(102L);
        d.setTransactionDate(date);
        d.setDeductedAmount(new BigDecimal(amount));
        return d;
    }

    private static EmployeePayrollSettings settings(long employeeId, PaymentMode mode) {
        EmployeePayrollSettings s = new EmployeePayrollSettings();
        s.setEmployeeId(employeeId);
        s.setPaymentMode(mode);
        return s;
    }

    private static AccountsReportEmployee employee(long id, String card, String name, String father) {
        return new AccountsReportEmployee(id, card, name, father, "35202-1234567-1", LocalDate.of(2020, 1, 1),
                EmploymentStatus.ACTIVE, "PERMANENT", 1L, "Production", 2L, "Stitching", 3L, "Stitcher", "G2",
                null, true);
    }

    private static AccountsReportFilter filter(java.util.function.Consumer<Builder> edit) {
        Builder b = new Builder();
        edit.accept(b);
        return b.build();
    }

    private static final class Builder {
        String fiscalYearCode;
        YearMonth month;
        Integer bonusNo;
        Long bankId;
        List<String> cardList;
        EmployeeStatusFilter status;

        AccountsReportFilter build() {
            return new AccountsReportFilter(fiscalYearCode, month, null, null, null, null, null, cardList,
                    null, null, null, status, null, bonusNo, null, null, null, null, null, null, null, bankId);
        }
    }
}
