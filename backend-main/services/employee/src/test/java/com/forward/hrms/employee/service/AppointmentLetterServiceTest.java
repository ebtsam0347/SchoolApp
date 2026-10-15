package com.forward.hrms.employee.service;

import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.ForbiddenError;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.core.error.UnprocessableError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.reports.JasperRenderer;
import com.forward.hrms.commons.storage.ObjectStorage;
import com.forward.hrms.employee.client.AttendanceClient;
import com.forward.hrms.employee.client.DependencyUnavailableError;
import com.forward.hrms.employee.client.PayrollClient;
import com.forward.hrms.employee.config.DocumentProperties;
import com.forward.hrms.employee.dto.request.AppointmentLetterRequest;
import com.forward.hrms.employee.entity.City;
import com.forward.hrms.employee.entity.Department;
import com.forward.hrms.employee.entity.Designation;
import com.forward.hrms.employee.entity.Employee;
import com.forward.hrms.employee.entity.EmployeeQualification;
import com.forward.hrms.employee.entity.EmployeeRequisitionDetail;
import com.forward.hrms.employee.entity.EmploymentContract;
import com.forward.hrms.employee.entity.Grade;
import com.forward.hrms.employee.entity.LetterClause;
import com.forward.hrms.employee.entity.LetterSettings;
import com.forward.hrms.employee.entity.Qualification;
import com.forward.hrms.employee.entity.Section;
import com.forward.hrms.employee.enums.ContractStatus;
import com.forward.hrms.employee.enums.ContractType;
import com.forward.hrms.employee.enums.DesignationCategory;
import com.forward.hrms.employee.enums.EmployeeErrorCode;
import com.forward.hrms.employee.enums.EmploymentType;
import com.forward.hrms.employee.enums.LetterClauseSection;
import com.forward.hrms.employee.enums.LetterType;
import com.forward.hrms.employee.repository.CityRepository;
import com.forward.hrms.employee.repository.DepartmentRepository;
import com.forward.hrms.employee.repository.DesignationRepository;
import com.forward.hrms.employee.repository.EmployeeQualificationRepository;
import com.forward.hrms.employee.repository.EmployeeRepository;
import com.forward.hrms.employee.repository.EmployeeRequisitionDetailRepository;
import com.forward.hrms.employee.repository.EmploymentContractRepository;
import com.forward.hrms.employee.repository.GradeRepository;
import com.forward.hrms.employee.repository.LetterClauseRepository;
import com.forward.hrms.employee.repository.LetterSettingsRepository;
import com.forward.hrms.employee.repository.QualificationRepository;
import com.forward.hrms.employee.repository.SectionRepository;
import com.forward.hrms.payroll.dto.response.EmployeeSalaryComponentDto;
import com.forward.hrms.payroll.dto.response.EmployeeSalaryDto;
import com.forward.hrms.payroll.enums.CalcType;
import com.forward.hrms.payroll.enums.ComponentType;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Appointment letter: validation paths with mocked collaborators, and a full
 * render through the real Jasper template with the paragraph and signatories
 * parsed from the {@code V0622} seed, so the text-extraction assertions cover
 * the shipped defaults rather than a copy. The PDF lands in
 * {@code target/report-previews/} for eyeballing.
 */
class AppointmentLetterServiceTest {

    private static final Path SEED = Path.of("src/main/resources/db/migration/V0622__letter_settings.sql");

    private final EmployeeRepository employees = mock(EmployeeRepository.class);
    private final DepartmentRepository departments = mock(DepartmentRepository.class);
    private final SectionRepository sections = mock(SectionRepository.class);
    private final DesignationRepository designations = mock(DesignationRepository.class);
    private final GradeRepository grades = mock(GradeRepository.class);
    private final CityRepository cities = mock(CityRepository.class);
    private final QualificationRepository qualificationCatalog = mock(QualificationRepository.class);
    private final EmployeeQualificationRepository qualifications = mock(EmployeeQualificationRepository.class);
    private final EmploymentContractRepository contracts = mock(EmploymentContractRepository.class);
    private final EmployeeRequisitionDetailRepository requisitionLines = mock(EmployeeRequisitionDetailRepository.class);
    private final LetterClauseRepository clauses = mock(LetterClauseRepository.class);
    private final LetterSettingsRepository settingsRepo = mock(LetterSettingsRepository.class);
    private final PayrollClient payroll = mock(PayrollClient.class);
    private final AttendanceClient attendance = mock(AttendanceClient.class);

    private AppointmentLetterService service;
    private Employee employee;

    @BeforeEach
    void setUp() throws Exception {
        LetterSettingsService letterSettings = new LetterSettingsService(settingsRepo, mock(ObjectStorage.class),
                new DocumentProperties("employee-docs", "employee-avatars", Duration.ofMinutes(10),
                        Duration.ofMinutes(10), Duration.ofDays(7), 10_000_000L, 2_000_000L));
        service = new AppointmentLetterService(employees, departments, sections, designations, grades, cities,
                qualificationCatalog, qualifications, contracts, requisitionLines, clauses, letterSettings,
                payroll, attendance, new JasperRenderer(), mock(AuditEmitter.class), mock(TransactionTemplate.class));

        when(settingsRepo.findFirstByOrderByIdAsc()).thenReturn(Optional.of(seededSettings()));
        when(clauses.findAllByLetterTypeAndActiveTrueOrderBySectionAscSeqAsc(LetterType.APPOINTMENT))
                .thenReturn(seededClauses());

        Department it = withId(new Department(), 1L);
        it.setName("Information Technology");
        it.setHeadId(102L);
        Section sysdev = withId(new Section(), 10L);
        sysdev.setName("System Development");
        Designation officer = withId(new Designation(), 20L);
        officer.setTitle("Executive Officer");
        officer.setCategory(DesignationCategory.INDIRECT);
        Grade m5 = withId(new Grade(), 30L);
        m5.setCode("M-5");
        City wazirabad = withId(new City(), 40L);
        wazirabad.setName("Wazirabad");
        Qualification bscs = withId(new Qualification(), 50L);
        bscs.setLevel("Graduation");
        bscs.setName("BS Computer Science");
        when(departments.findById(1L)).thenReturn(Optional.of(it));
        when(sections.findById(10L)).thenReturn(Optional.of(sysdev));
        when(designations.findById(20L)).thenReturn(Optional.of(officer));
        when(grades.findById(30L)).thenReturn(Optional.of(m5));
        when(cities.findById(40L)).thenReturn(Optional.of(wazirabad));
        when(qualificationCatalog.findById(50L)).thenReturn(Optional.of(bscs));

        employee = new Employee();
        employee.setId(100L);
        employee.setEmployeeCode("11405");
        employee.setFullName("Ebtasam Ur Rehman");
        employee.setFatherName("Abdul Manan");
        employee.setGender("MALE");
        employee.setMaritalStatus("SINGLE");
        employee.setReligion("Islam");
        employee.setCnic("34104-1285675-5");
        employee.setDateOfBirth(LocalDate.of(2000, 9, 16));
        employee.setPhone("03474319625");
        employee.setCityId(40L);
        employee.setPermanentAddress("P.O Khas Talwara Teh Wazirabad Distt Gujranwala");
        employee.setCurrentAddress("P.O Khas Talwara Teh Wazirabad Distt Gujranwala");
        employee.setJoiningDate(LocalDate.of(2023, 10, 5));
        employee.setConfirmationDate(LocalDate.of(2024, 1, 5));
        employee.setEmploymentType(EmploymentType.PERMANENT);
        employee.setDepartmentId(1L);
        employee.setSectionId(10L);
        employee.setDesignationId(20L);
        employee.setGradeId(30L);
        employee.setManagerId(101L);
        Employee hod = new Employee();
        hod.setId(102L);
        hod.setFullName("Sara Khan");
        Employee replaced = new Employee();
        replaced.setId(103L);
        replaced.setEmployeeCode("10988");
        when(employees.findById(100L)).thenReturn(Optional.of(employee));
        when(employees.findById(102L)).thenReturn(Optional.of(hod));
        when(employees.findById(103L)).thenReturn(Optional.of(replaced));

        EmployeeQualification q = new EmployeeQualification();
        q.setQualificationId(50L);
        when(qualifications.findAllByEmployeeIdOrderByYearOfCompletionDescIdAsc(100L)).thenReturn(List.of(q));

        EmployeeRequisitionDetail line = new EmployeeRequisitionDetail();
        line.setRequisitionHeadId(77L);
        line.setReplacingEmployeeId(103L);
        when(requisitionLines.findFirstByNewEmployeeCodeOrderByIdDesc("11405")).thenReturn(Optional.of(line));

        EmploymentContract probation = new EmploymentContract();
        probation.setContractType(ContractType.PROBATION);
        probation.setStatus(ContractStatus.ACTIVE);
        probation.setProbationDays(90);
        when(contracts.findAllByEmployeeIdOrderByStartDateDesc(100L)).thenReturn(List.of(probation));

        when(attendance.currentShiftName(100L)).thenReturn(Optional.of("Day"));
        when(payroll.currentSalary(100L)).thenReturn(Optional.of(
                new EmployeeSalaryDto(1L, 100L, new BigDecimal("65292.00"), LocalDate.of(2023, 10, 5), null, null)));
        when(payroll.currentComponents(100L)).thenReturn(List.of(
                earning("House Rent Allowance", "9000.00", CalcType.FLAT),
                earning("Meal Allowance", "250.00", CalcType.PER_DAY),
                new EmployeeSalaryComponentDto(9L, 100L, 9L, "INCOME_TAX", "Income Tax", true,
                        ComponentType.DEDUCTION, CalcType.FLAT, new BigDecimal("1200.00"), null, null, null)));

        signIn("payroll.read", "employee.reports.run");
    }

    @AfterEach
    void tearDown() {
        CurrentUserHolder.clear();
    }

    @Test
    void rendersTheFormLayoutOnOnePage() throws Exception {
        AppointmentLetterService.Rendered letter = service.render(100L, AppointmentLetterRequest.defaults(), null);

        assertThat(letter.filename()).isEqualTo("appointment-letter-11405.pdf");
        assertThat(letter.refNo()).isEqualTo("FS/HR/APPT/" + LocalDate.now().getYear() + "/11405");
        Files.createDirectories(Path.of("target", "report-previews"));
        Files.write(Path.of("target", "report-previews", "appointment-letter.pdf"), letter.pdf());

        PdfReader reader = new PdfReader(letter.pdf());
        assertThat(reader.getNumberOfPages()).isEqualTo(1);
        String text = text(reader);
        assertThat(text.trim()).startsWith("FORWARD SPORTS (PVT.) LTD.");
        assertThat(text).contains(
                "WAZIRABAD ROAD SAHOWALA STOP, SIALKOT (Ph.#: 052-3571800-5)",
                "Appointment Letter", "Employee Job Type: Permanent",
                "Mr. Ebtasam Ur Rehman S/O Abdul Manan is appointed in Section System Development as Executive Officer.",
                "He is appointed with reference to his application", "The first three months",
                "Personal Information:", "Official Information:",
                "Card No:", "11405", "Father Name:", "CNIC:", "34104-1285675-5", "Date Of Birth:", "16/09/2000",
                "Marital Status:", "Single", "Qualification:", "Graduation", "Specialization:", "BS Computer Science",
                "Contact No:", "03474319625", "City:", "Wazirabad",
                "Date Of Joining:", "05/10/2023", "Date Of Permanent:", "05/01/2024",
                "Department:", "Information Technology", "Head Of Department:", "Sara Khan",
                "Designation:", "Executive Officer", "Basic Salary:", "65,292",
                "Special Allowance:0", "Accommodation:0",
                "House Rent Allowance:", "9,000", "Meal Allowance:", "250 / day",
                "Grade:", "M-5", "Employment:", "Indirect", "Shift:", "Day", "Branch:", "Main Branch (Sahowala)",
                "Reference:", "Request ID:", "77", "Request Against Card No:", "10988", "Any Other Info:",
                "Husband Name:",
                "General Manager", "Human Resource", "Deputy Director", "Accounts Manager");
        assertThat(text).doesNotContain("Income Tax", "{");
        // Bold really is bold: without an explicit pdfFontName, Jasper exports "bold" as regular Helvetica.
        assertThat(new String(letter.pdf(), StandardCharsets.ISO_8859_1)).contains("/Helvetica-Bold");
    }

    @Test
    void femaleEmployeeGetsHusbandRowAndPronouns() throws Exception {
        employee.setGender("FEMALE");
        employee.setSpouseName("Ali Raza");

        String text = text(new PdfReader(service.render(100L, withoutSalary(), null).pdf()));

        assertThat(text).contains("Ms. Ebtasam Ur Rehman D/O Abdul Manan", "She is appointed", "her application",
                "Husband Name:", "Ali Raza");
    }

    @Test
    void assignedFormAllowancesPrintTheirAmountAheadOfOtherEarnings() throws Exception {
        when(payroll.currentComponents(100L)).thenReturn(List.of(
                earning("House Rent Allowance", "9000.00", CalcType.FLAT),
                new EmployeeSalaryComponentDto(8L, 100L, 8L, "SPECIAL", "Special Allowance", true,
                        ComponentType.EARNING, CalcType.FLAT, new BigDecimal("1500.00"), null, null, null)));

        String text = text(new PdfReader(service.render(100L, AppointmentLetterRequest.defaults(), null).pdf()));

        assertThat(text).contains("Special Allowance:1,500", "Accommodation:0");
        assertThat(text.indexOf("Accommodation:")).isLessThan(text.indexOf("House Rent Allowance:"));
    }

    @Test
    void reportsEveryMissingFieldInOne422() {
        employee.setDesignationId(null);
        employee.setJoiningDate(null);

        assertThatThrownBy(() -> service.render(100L, withoutSalary(), null))
                .isInstanceOfSatisfying(UnprocessableError.class, ex -> {
                    assertThat(ex.code()).isEqualTo(EmployeeErrorCode.APPOINTMENT_LETTER_DATA_MISSING);
                    assertThat(ex.getMessage()).contains("designation, joiningDate");
                    assertThat(ex.extensions()).containsEntry("missingFields", List.of("designation", "joiningDate"));
                });
    }

    @Test
    void missingSalaryIs422NeverAZeroSalary() {
        when(payroll.currentSalary(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.render(100L, AppointmentLetterRequest.defaults(), null))
                .isInstanceOfSatisfying(UnprocessableError.class,
                        ex -> assertThat(ex.getMessage()).contains("basicSalary"));
    }

    @Test
    void salaryWithoutPayrollReadIs403AndPayrollIsNotCalled() {
        signIn("employee.reports.run");

        assertThatThrownBy(() -> service.render(100L, AppointmentLetterRequest.defaults(), null))
                .isInstanceOf(ForbiddenError.class);
        verify(payroll, never()).currentSalary(anyLong());
    }

    @Test
    void letterWithoutSalaryNeedsNoPayrollAccess() throws Exception {
        signIn("employee.reports.run");

        String text = text(new PdfReader(service.render(100L, withoutSalary(), null).pdf()));

        assertThat(text).contains("Official Information:").doesNotContain("Basic Salary");
        verify(payroll, never()).currentSalary(anyLong());
    }

    @Test
    void payrollOutagePropagatesAs503() {
        when(payroll.currentSalary(100L)).thenThrow(new DependencyUnavailableError("payroll down", null));

        assertThatThrownBy(() -> service.render(100L, AppointmentLetterRequest.defaults(), null))
                .isInstanceOfSatisfying(DependencyUnavailableError.class, ex -> assertThat(ex.status()).isEqualTo(503));
    }

    @Test
    void managerCannotPrintSomeoneElsesLetter() {
        assertThatThrownBy(() -> service.render(100L, withoutSalary(), 999L)).isInstanceOf(NotFoundError.class);
    }

    @Test
    void overridesWinOverEmployeeData() throws Exception {
        AppointmentLetterRequest req = new AppointmentLetterRequest(LocalDate.of(2026, 9, 25),
                LocalDate.of(2026, 11, 2), 6, false, "FS/HR/APPT/CUSTOM-1", "Head Office (Sialkot)", "Transferred from QA");

        AppointmentLetterService.Rendered letter = service.render(100L, req, null);

        assertThat(letter.refNo()).isEqualTo("FS/HR/APPT/CUSTOM-1");
        assertThat(text(new PdfReader(letter.pdf())))
                .contains("25 September 2026", "02/11/2026", "The first six months", "FS/HR/APPT/CUSTOM-1",
                        "Head Office (Sialkot)", "Transferred from QA");
    }

    @Test
    void markupCharactersInDataAreEscaped() throws Exception {
        employee.setFullName("Ali <Test> & Sons");

        String text = text(new PdfReader(service.render(100L, withoutSalary(), null).pdf()));

        assertThat(text).contains("Mr. Ali <Test> & Sons S/O");
    }

    @Test
    void unknownPlaceholdersStayVisible() {
        assertThat(AppointmentLetterService.fill("Hello {employeeName}, see {typo}.", Map.of("employeeName", "Ali")))
                .isEqualTo("Hello Ali, see {typo}.");
    }

    // ---------- fixtures ----------

    private static AppointmentLetterRequest withoutSalary() {
        return new AppointmentLetterRequest(null, null, null, false, null, null, null);
    }

    private static void signIn(String... permissions) {
        CurrentUserHolder.set(new CurrentUser(1L, "hr.admin", "HR Admin", 1L, Set.of("HR"), Set.of(permissions)));
    }

    private static EmployeeSalaryComponentDto earning(String name, String amount, CalcType calc) {
        return new EmployeeSalaryComponentDto(name.hashCode(), 100L, name.hashCode(), name.toUpperCase(), name, true,
                ComponentType.EARNING, calc, new BigDecimal(amount), null, null, null);
    }

    private static String text(PdfReader reader) throws Exception {
        PdfTextExtractor extractor = new PdfTextExtractor(reader);
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= reader.getNumberOfPages(); i++) sb.append(extractor.getTextFromPage(i)).append('\n');
        // Collapse wrapping so phrases split across lines still match.
        return sb.toString().replaceAll("\\s+", " ");
    }

    private static LetterSettings seededSettings() {
        LetterSettings s = new LetterSettings();
        s.setCompanyLegalName("FORWARD SPORTS (PVT.) LTD.");
        s.setCompanyAddress("WAZIRABAD ROAD SAHOWALA STOP, SIALKOT");
        s.setCompanyPhone("052-3571800-5");
        s.setRefPrefix("FS/HR");
        s.setSignatoryName("General Manager");
        s.setSignatoryDesignation("Human Resource");
        s.setDefaultProbationMonths(3);
        s.setDefaultBranch("Main Branch (Sahowala)");
        return s;
    }

    /** The APPOINTMENT clause rows exactly as V0622 seeds them. */
    private static List<LetterClause> seededClauses() throws Exception {
        String sql = Files.readString(SEED, StandardCharsets.UTF_8);
        Matcher m = Pattern.compile(
                "\\('APPOINTMENT',\\s*'(\\w+)',\\s*(\\d+),\\s*(null|'(?:[^']|'')*'),\\s*'((?:[^']|'')*)',\\s*'system'")
                .matcher(sql);
        List<LetterClause> rows = new ArrayList<>();
        while (m.find()) {
            LetterClause c = new LetterClause();
            c.setLetterType(LetterType.APPOINTMENT);
            c.setSection(LetterClauseSection.valueOf(m.group(1)));
            c.setSeq(Integer.parseInt(m.group(2)));
            c.setTitle("null".equals(m.group(3)) ? null : m.group(3).substring(1, m.group(3).length() - 1).replace("''", "'"));
            c.setBody(m.group(4).replace("''", "'"));
            rows.add(c);
        }
        assertThat(rows).as("clauses parsed from V0622").hasSize(4);
        return rows;
    }

    private static <T extends com.forward.hrms.commons.core.jpa.BaseLongIdEntity> T withId(T entity, long id) {
        entity.setId(id);
        return entity;
    }
}
