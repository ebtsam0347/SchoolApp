package com.forward.hrms.payroll.service;

import com.forward.hrms.commons.cache.EmployeeDisplay;
import com.forward.hrms.commons.cache.EmployeeDisplayCache;
import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.core.error.UnprocessableError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.reports.JasperRenderer;
import com.forward.hrms.employee.enums.EmploymentStatus;
import com.forward.hrms.payroll.client.EmployeeClient;
import com.forward.hrms.payroll.entity.Bank;
import com.forward.hrms.payroll.entity.EmployeeBankDetails;
import com.forward.hrms.payroll.entity.EmployeeSalary;
import com.forward.hrms.payroll.entity.EmployeeSalaryComponent;
import com.forward.hrms.payroll.entity.SalaryComponent;
import com.forward.hrms.payroll.enums.CalcType;
import com.forward.hrms.payroll.enums.ComponentType;
import com.forward.hrms.payroll.repository.BankRepository;
import com.forward.hrms.payroll.repository.EmployeeBankDetailsRepository;
import com.forward.hrms.payroll.repository.EmployeeSalaryComponentRepository;
import com.forward.hrms.payroll.repository.EmployeeSalaryRepository;
import com.forward.hrms.payroll.repository.SalaryComponentRepository;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;

import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Salary certificate and salary increment list through the real Jasper
 * template / POI writer with mocked repositories. Outputs land in
 * {@code target/report-previews/}.
 */
class PayrollLetterReportsTest {

    private final EmployeeClient employees = mock(EmployeeClient.class);
    private final EmployeeDisplayCache displays = mock(EmployeeDisplayCache.class);
    private final EmployeeSalaryRepository salaries = mock(EmployeeSalaryRepository.class);
    private final EmployeeSalaryComponentRepository employeeComponents = mock(EmployeeSalaryComponentRepository.class);
    private final SalaryComponentRepository components = mock(SalaryComponentRepository.class);
    private final EmployeeBankDetailsRepository bankDetails = mock(EmployeeBankDetailsRepository.class);
    private final BankRepository banks = mock(BankRepository.class);

    private EmployeeClient.EmployeeBrief ali;

    @BeforeEach
    void setUp() {
        ali = new EmployeeClient.EmployeeBrief(100L, "11405", "Ebtasam Ur Rehman", "34104-1285675-5", null, null,
                null, 20L, EmploymentStatus.ACTIVE, LocalDate.of(2023, 10, 5), "Abdul Manan");
        when(employees.getMany(List.of(100L))).thenReturn(Map.of(100L, ali));
        when(displays.get(100L)).thenReturn(display(100L, "11405", "Ebtasam Ur Rehman", 2L, "Information Technology"));

        when(salaries.findCurrent(100L)).thenReturn(Optional.of(salary(1L, 100L, "65292.00", LocalDate.of(2023, 10, 5), null)));
        SalaryComponent hra = component(1L, "HRA", "House Rent Allowance", CalcType.FLAT, 10);
        SalaryComponent special = component(2L, "SPECIAL", "Special Allowance", CalcType.FLAT, 45);
        SalaryComponent meal = component(3L, "MEAL", "Meal Allowance", CalcType.PER_DAY, 40);
        when(employeeComponents.findCurrent(100L, ComponentType.EARNING)).thenReturn(List.of(
                line(100L, 2L, "1500.00"), line(100L, 1L, "9000.00"), line(100L, 3L, "250.00")));
        when(components.findAllById(any())).thenReturn(List.of(hra, special, meal));

        EmployeeBankDetails acct = new EmployeeBankDetails();
        acct.setEmployeeId(100L);
        acct.setBankId(7L);
        acct.setAccountNumber("0123-4567890123");
        acct.setAccountTitle("Ebtasam Ur Rehman");
        acct.setBranch("Sialkot Cantt");
        Bank meezan = new Bank();
        meezan.setId(7L);
        meezan.setName("Meezan Bank");
        when(bankDetails.findByEmployeeId(100L)).thenReturn(Optional.of(acct));
        when(banks.findById(7L)).thenReturn(Optional.of(meezan));

        CurrentUserHolder.set(new CurrentUser(1L, "accounts", "Accounts", 1L, Set.of("ACCOUNTS"), Set.of("payroll.reports.run")));
    }

    @AfterEach
    void tearDown() {
        CurrentUserHolder.clear();
    }

    // ---------- salary certificate ----------

    @Test
    void certificateShowsFixedSalaryGrossInWordsAndAccount() throws Exception {
        String text = savePdf(certificates().render(100L, "The Branch Manager, Meezan Bank, Sialkot", "car loan application"),
                "salary-certificate.pdf");

        assertThat(text).contains("FORWARD SPORTS (PVT.) LTD.", "SALARY CERTIFICATE", "Ref: FS/ACC/SAL/",
                "The Branch Manager, Meezan Bank, Sialkot",
                "Ebtasam Ur Rehman, son/daughter of Abdul Manan, CNIC No. 34104-1285675-5, Card No. 11405",
                "since 05 October 2023", "in the Information Technology department",
                "Basic Salary", "Rs 65,292.00", "House Rent Allowance", "Rs 9,000.00", "Special Allowance", "Rs 1,500.00",
                "Gross Monthly Salary", "Rs 75,792.00", "Seventy Five Thousand Seven Hundred Ninety Two",
                "account No. 0123-4567890123 (Ebtasam Ur Rehman), Meezan Bank, Sialkot Cantt",
                "for car loan application without any financial liability", "Accounts Manager", "General Manager HR");
        assertThat(text).doesNotContain("Meal Allowance");
        assertThat(text.indexOf("House Rent Allowance")).isLessThan(text.indexOf("Special Allowance"));
    }

    @Test
    void certificateRefusesLeaversMissingSalaryAndUnknownEmployees() {
        when(employees.getMany(List.of(101L))).thenReturn(Map.of(101L, EmployeeClient.EmployeeBrief.placeholder(101L)));
        assertThatThrownBy(() -> certificates().render(101L, null, null)).isInstanceOf(NotFoundError.class);

        when(salaries.findCurrent(100L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> certificates().render(100L, null, null))
                .isInstanceOfSatisfying(UnprocessableError.class, ex -> assertThat(ex.getMessage()).contains("No basic salary"));

        when(employees.getMany(List.of(100L))).thenReturn(Map.of(100L, new EmployeeClient.EmployeeBrief(100L, "11405",
                "Ebtasam Ur Rehman", null, null, null, null, null, EmploymentStatus.TERMINATED, null, null)));
        assertThatThrownBy(() -> certificates().render(100L, null, null))
                .isInstanceOfSatisfying(UnprocessableError.class, ex -> assertThat(ex.getMessage()).contains("has left"));
    }

    // ---------- increment list ----------

    @Test
    void incrementListPairsEachRevisionWithThePreviousBasic() throws Exception {
        when(salaries.findAll()).thenReturn(List.of(
                salary(1L, 100L, "60000.00", LocalDate.of(2023, 10, 5), null),
                salary(2L, 100L, "65292.00", LocalDate.of(2026, 7, 1), "Annual increment 2026"),
                salary(3L, 101L, "40000.00", LocalDate.of(2024, 1, 1), null),
                salary(4L, 101L, "44000.00", LocalDate.of(2026, 7, 1), "Annual increment 2026"),
                salary(5L, 102L, "30000.00", LocalDate.of(2026, 7, 15), null)));
        when(displays.getMany(anyCollection())).thenReturn(Map.of(
                100L, display(100L, "11405", "Ebtasam Ur Rehman", 2L, "Information Technology"),
                101L, display(101L, "9800", "Bilal Ahmed", 1L, "Production")));
        IncrementListReportService svc = new IncrementListReportService(salaries, displays, mock(AuditEmitter.class));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        svc.writeXlsx(out, LocalDate.of(2026, 7, 1), null, null);
        save(out.toByteArray(), "salary-increments.xlsx");

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
            var sheet = wb.getSheetAt(0);
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("Salary Increment List");
            assertThat(sheet.getRow(2).getCell(0).getStringCellValue()).contains("01-Jul-2026 to 31-Jul-2026");
            assertThat(cells(sheet.getRow(6))).containsExactly("1", "01-Jul-2026", "9800", "Bilal Ahmed", "Production",
                    "", "Executive Officer", "40000", "44000", "4000", "10", "Annual increment 2026");
            assertThat(cells(sheet.getRow(7))).contains("11405", "60000", "65292", "5292", "8.82");
            assertThat(cells(sheet.getRow(8))).contains("Total", "2 revision(s)", "9292");
        }

        ByteArrayOutputStream it = new ByteArrayOutputStream();
        svc.writeXlsx(it, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), 2L);
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(it.toByteArray()))) {
            assertThat(wb.getSheetAt(0).getRow(2).getCell(0).getStringCellValue()).contains("Department: Information Technology");
            assertThat(cells(wb.getSheetAt(0).getRow(7))).contains("1 revision(s)");
        }
        assertThatThrownBy(() -> svc.writeXlsx(new ByteArrayOutputStream(), LocalDate.of(2026, 8, 1), LocalDate.of(2026, 7, 1), null))
                .isInstanceOf(UnprocessableError.class);
    }

    // ---------- joining vs gratuity date ----------

    @Test
    void gratuityDatesListOnlyEmployeesCountedFromAnotherDate() throws Exception {
        var settingsRepo = mock(com.forward.hrms.payroll.repository.EmployeePayrollSettingsRepository.class);
        when(settingsRepo.findAllByActiveTrue()).thenReturn(List.of(
                settings(100L, LocalDate.of(2023, 10, 5), null),                          // same as joining
                settings(101L, LocalDate.of(2024, 1, 1), LocalDate.of(2025, 3, 1)),       // rejoined: gratuity reset
                settings(102L, LocalDate.of(2019, 1, 1), null),                          // hire date ≠ joining
                settings(103L, LocalDate.of(2020, 1, 1), LocalDate.of(2021, 1, 1))));     // left
        when(employees.getMany(anyCollection())).thenReturn(Map.of(
                100L, ali,
                101L, brief(101L, "9800", "Bilal Ahmed", EmploymentStatus.ACTIVE, LocalDate.of(2024, 1, 1)),
                102L, brief(102L, "7001", "Usman Tariq", EmploymentStatus.ACTIVE, LocalDate.of(2019, 2, 1)),
                103L, brief(103L, "5000", "Old Timer", EmploymentStatus.TERMINATED, LocalDate.of(2020, 1, 1))));
        when(displays.getMany(anyCollection())).thenReturn(Map.of(
                101L, display(101L, "9800", "Bilal Ahmed", 1L, "Production")));
        GratuityDatesReportService svc = new GratuityDatesReportService(settingsRepo, employees, displays, mock(AuditEmitter.class));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        svc.writeXlsx(out, true, null);
        save(out.toByteArray(), "gratuity-dates.xlsx");

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
            var sheet = wb.getSheetAt(0);
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("Joining vs Gratuity Date");
            assertThat(cells(sheet.getRow(6))).startsWith("1", "7001", "Usman Tariq")
                    .contains("01-Feb-2019", "01-Jan-2019", "-31");
            assertThat(cells(sheet.getRow(7))).startsWith("2", "9800", "Bilal Ahmed")
                    .contains("Production", "01-Jan-2024", "01-Mar-2025", "425");
            assertThat(cells(sheet.getRow(8))).contains("Total", "2 employee(s)");
        }
        ByteArrayOutputStream all = new ByteArrayOutputStream();
        svc.writeXlsx(all, false, null);
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(all.toByteArray()))) {
            assertThat(cells(wb.getSheetAt(0).getRow(9))).contains("3 employee(s)");
        }
    }

    // ---------- fixtures ----------

    private static com.forward.hrms.payroll.entity.EmployeePayrollSettings settings(long employeeId, LocalDate hire, LocalDate gratuity) {
        var s = new com.forward.hrms.payroll.entity.EmployeePayrollSettings();
        s.setEmployeeId(employeeId);
        s.setHireDate(hire);
        s.setGratuityStartDate(gratuity);
        return s;
    }

    private static EmployeeClient.EmployeeBrief brief(long id, String code, String name, EmploymentStatus status, LocalDate joined) {
        return new EmployeeClient.EmployeeBrief(id, code, name, null, null, null, null, null, status, joined, null);
    }

    private SalaryCertificateService certificates() {
        return new SalaryCertificateService(employees, displays, salaries, employeeComponents, components, bankDetails,
                banks, new JasperRenderer(), mock(AuditEmitter.class));
    }

    private static EmployeeDisplay display(long id, String code, String name, Long deptId, String dept) {
        return new EmployeeDisplay(id, code, name, deptId, dept, null, null, 20L, "Executive Officer", null);
    }

    private static EmployeeSalary salary(long id, long employeeId, String basic, LocalDate from, String reason) {
        EmployeeSalary s = new EmployeeSalary();
        s.setId(id);
        s.setEmployeeId(employeeId);
        s.setBasic(new BigDecimal(basic));
        s.setEffectiveFrom(from);
        s.setRevisionReason(reason);
        return s;
    }

    private static SalaryComponent component(long id, String code, String name, CalcType calc, int order) {
        SalaryComponent c = new SalaryComponent();
        c.setId(id);
        c.setCode(code);
        c.setName(name);
        c.setType(ComponentType.EARNING);
        c.setCalcType(calc);
        c.setDisplayOrder(order);
        return c;
    }

    private static EmployeeSalaryComponent line(long employeeId, long componentId, String amount) {
        EmployeeSalaryComponent l = new EmployeeSalaryComponent();
        l.setEmployeeId(employeeId);
        l.setComponentId(componentId);
        l.setAmount(new BigDecimal(amount));
        return l;
    }

    private static List<String> cells(Row row) {
        List<String> out = new ArrayList<>();
        for (int c = 0; c < row.getLastCellNum(); c++) {
            var cell = row.getCell(c);
            if (cell == null) out.add("");
            else if (cell.getCellType() == CellType.NUMERIC) {
                BigDecimal d = BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros();
                out.add(d.toPlainString());
            } else out.add(cell.getStringCellValue());
        }
        return out;
    }

    private static void save(byte[] bytes, String name) throws Exception {
        Path dir = Path.of("target", "report-previews");
        Files.createDirectories(dir);
        Files.write(dir.resolve(name), bytes);
    }

    private static String savePdf(byte[] pdf, String name) throws Exception {
        save(pdf, name);
        PdfReader reader = new PdfReader(pdf);
        PdfTextExtractor extractor = new PdfTextExtractor(reader);
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= reader.getNumberOfPages(); i++) sb.append(extractor.getTextFromPage(i)).append('\n');
        return sb.toString().replaceAll("\\s+", " ");
    }
}
