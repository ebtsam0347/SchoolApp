package com.forward.hrms.employee.service;

import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.reports.JasperRenderer;
import com.forward.hrms.commons.storage.ObjectStorage;
import com.forward.hrms.employee.client.PayrollClient;
import com.forward.hrms.employee.config.DocumentProperties;
import com.forward.hrms.employee.entity.Department;
import com.forward.hrms.employee.entity.Designation;
import com.forward.hrms.employee.entity.EmergencyContact;
import com.forward.hrms.employee.entity.Employee;
import com.forward.hrms.employee.entity.EmployeeHistory;
import com.forward.hrms.employee.entity.EmploymentContract;
import com.forward.hrms.employee.entity.Grade;
import com.forward.hrms.employee.entity.LetterSettings;
import com.forward.hrms.employee.entity.Section;
import com.forward.hrms.employee.enums.ContractStatus;
import com.forward.hrms.employee.enums.ContractType;
import com.forward.hrms.employee.enums.EmploymentStatus;
import com.forward.hrms.employee.enums.EmploymentType;
import com.forward.hrms.employee.enums.HistoryChangeReason;
import com.forward.hrms.employee.repository.BelongingIssuanceRepository;
import com.forward.hrms.employee.repository.CityRepository;
import com.forward.hrms.employee.repository.DepartmentRepository;
import com.forward.hrms.employee.repository.DesignationRepository;
import com.forward.hrms.employee.repository.DisciplinaryActionRepository;
import com.forward.hrms.employee.repository.EmergencyContactRepository;
import com.forward.hrms.employee.repository.EmployeeCertificationRepository;
import com.forward.hrms.employee.repository.EmployeeDocumentRepository;
import com.forward.hrms.employee.repository.EmployeeHistoryRepository;
import com.forward.hrms.employee.repository.EmployeePriorExperienceRepository;
import com.forward.hrms.employee.repository.EmployeeQualificationRepository;
import com.forward.hrms.employee.repository.EmployeeRepository;
import com.forward.hrms.employee.repository.EmployeeSkillRepository;
import com.forward.hrms.employee.repository.EmployeeSuspensionRepository;
import com.forward.hrms.employee.repository.EmploymentContractRepository;
import com.forward.hrms.employee.repository.GradeRepository;
import com.forward.hrms.employee.repository.LetterSettingsRepository;
import com.forward.hrms.employee.repository.QualificationRepository;
import com.forward.hrms.employee.repository.SectionRepository;
import com.forward.hrms.employee.repository.SkillRepository;

import com.forward.hrms.payroll.dto.response.EmployeeSalaryComponentDto;
import com.forward.hrms.payroll.dto.response.EmployeeSalaryDto;
import com.forward.hrms.payroll.enums.CalcType;
import com.forward.hrms.payroll.enums.ComponentType;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Renders the employee profile and department strength PDFs through the
 * real Jasper templates with mocked repositories — catches template compile
 * errors and field/parameter mismatches that only surface at fill time.
 * PDFs land in {@code target/report-previews/} for eyeballing.
 */
class EmployeePdfReportsTest {

    private final EmployeeRepository employees = mock(EmployeeRepository.class);
    private final DepartmentRepository departments = mock(DepartmentRepository.class);
    private final SectionRepository sections = mock(SectionRepository.class);
    private final DesignationRepository designations = mock(DesignationRepository.class);
    private final GradeRepository grades = mock(GradeRepository.class);
    private final EmergencyContactRepository contacts = mock(EmergencyContactRepository.class);
    private final EmploymentContractRepository contracts = mock(EmploymentContractRepository.class);
    private final EmployeeHistoryRepository history = mock(EmployeeHistoryRepository.class);
    private final LetterSettingsRepository settingsRepo = mock(LetterSettingsRepository.class);
    private final ObjectStorage storage = mock(ObjectStorage.class);
    private final PayrollClient payroll = mock(PayrollClient.class);
    private final JasperRenderer jasper = new JasperRenderer();

    private LetterSettingsService letterSettings;

    @BeforeEach
    void setUp() {
        LetterSettings settings = new LetterSettings();
        settings.setCompanyLegalName("FORWARD SPORTS (PVT.) LTD.");
        settings.setCompanyAddress("WAZIRABAD ROAD SAHOWALA STOP, SIALKOT");
        when(settingsRepo.findFirstByOrderByIdAsc()).thenReturn(Optional.of(settings));
        letterSettings = new LetterSettingsService(settingsRepo, storage,
                new DocumentProperties("employee-docs", "employee-avatars", Duration.ofMinutes(10),
                        Duration.ofMinutes(10), Duration.ofDays(7), 10_000_000L, 2_000_000L));

        Department production = withId(new Department(), 1L);
        production.setName("Production");
        Department hr = withId(new Department(), 2L);
        hr.setName("Human Resources");
        Section stitching = withId(new Section(), 10L);
        stitching.setName("Stitching");
        Designation operator = withId(new Designation(), 20L);
        operator.setTitle("Machine Operator");
        Designation officer = withId(new Designation(), 21L);
        officer.setTitle("HR Officer");
        Grade g5 = withId(new Grade(), 30L);
        g5.setCode("G5");
        g5.setName("Officer");

        Employee ali = employee(100L, "FS-2024-0007", "Muhammad Ali", "MALE", 1L, 10L, 20L);
        ali.setGradeId(30L);
        ali.setFatherName("Muhammad Aslam");
        ali.setCnic("34603-1234567-1");
        ali.setDateOfBirth(LocalDate.of(1990, 5, 14));
        ali.setMaritalStatus("MARRIED");
        ali.setPhone("0300-1234567");
        ali.setCurrentAddress("House 12, Street 4, Model Town, Sialkot — a long address that must wrap onto a second line cleanly");
        ali.setEobiNumber("EOBI-998877");
        ali.setJoiningDate(LocalDate.of(2024, 3, 1));
        ali.setEmploymentType(EmploymentType.PERMANENT);
        ali.setManagerId(101L);
        ali.setProfilePictureKey("employees/100/avatar.jpg");
        Employee sara = employee(101L, "FS-2023-0002", "Sara Khan", "FEMALE", 2L, null, 21L);

        when(departments.findAll()).thenReturn(List.of(production, hr));
        when(sections.findAll()).thenReturn(List.of(stitching));
        when(designations.findAll()).thenReturn(List.of(operator, officer));
        when(grades.findAll()).thenReturn(List.of(g5));
        when(employees.findAll()).thenReturn(List.of(ali, sara));
        when(employees.findById(100L)).thenReturn(Optional.of(ali));
        when(employees.findById(101L)).thenReturn(Optional.of(sara));
        when(storage.getObject(anyString(), anyString())).thenThrow(new RuntimeException("no such key"));

        EmergencyContact ec = new EmergencyContact();
        ec.setName("Aslam");
        ec.setRelation("Father");
        ec.setPhone("0301-7654321");
        ec.setPrimary(true);
        when(contacts.findAllByEmployeeIdOrderByPrimaryDescIdAsc(100L)).thenReturn(List.of(ec));

        EmploymentContract c = new EmploymentContract();
        c.setContractType(ContractType.PROBATION);
        c.setStatus(ContractStatus.ACTIVE);
        c.setStartDate(LocalDate.of(2024, 3, 1));
        c.setEndDate(LocalDate.of(2024, 8, 31));
        c.setProbationDays(180);
        when(contracts.findAllByEmployeeIdOrderByStartDateDesc(100L)).thenReturn(List.of(c));

        EmployeeHistory h = new EmployeeHistory();
        h.setEffectiveFrom(LocalDate.of(2024, 3, 1));
        h.setDepartmentId(1L);
        h.setDesignationId(20L);
        h.setGradeId(30L);
        h.setChangeReason(HistoryChangeReason.ONBOARD);
        when(history.findAllByEmployeeIdOrderByEffectiveFromDesc(eq(100L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(h)));
    }

    @Test
    void employeeProfileRendersEverySection() throws Exception {
        byte[] pdf = profileService().render(100L, null);

        String text = save(pdf, "employee-profile.pdf");
        assertThat(text.trim()).startsWith("FORWARD SPORTS (PVT.) LTD.");
        assertThat(text).contains("Employee Profile", "Muhammad Ali", "FS-2024-0007", "No photo on file",
                "PERSONAL INFORMATION", "CONTACT DETAILS", "EMPLOYMENT", "STATUTORY NUMBERS",
                "EMERGENCY CONTACTS", "EMPLOYMENT CONTRACTS", "CAREER HISTORY", "01-Mar-2024 to Present",
                "QUALIFICATIONS", "PREVIOUS EXPERIENCE", "CERTIFICATIONS", "SKILLS", "DOCUMENTS ON FILE",
                "DISCIPLINARY RECORD", "SUSPENSIONS", "COMPANY BELONGINGS",
                "Sara Khan (FS-2023-0002)", "EOBI-998877", "Employee Signature", "HR Department");
        assertThat(new String(pdf, StandardCharsets.ISO_8859_1)).contains("/Helvetica-Bold");
    }

    @Test
    void profileShowsSalaryOnlyWithPayrollRead() throws Exception {
        when(payroll.currentSalary(100L)).thenReturn(Optional.of(
                new EmployeeSalaryDto(1L, 100L, new BigDecimal("65292.00"), LocalDate.of(2024, 3, 1), null, null)));
        when(payroll.currentComponents(100L)).thenReturn(List.of(
                new EmployeeSalaryComponentDto(1L, 100L, 1L, "HRA", "House Rent Allowance", true,
                        ComponentType.EARNING, CalcType.FLAT, new BigDecimal("9000.00"), LocalDate.of(2024, 3, 1), null, null),
                new EmployeeSalaryComponentDto(2L, 100L, 2L, "MEAL", "Meal Allowance", true,
                        ComponentType.EARNING, CalcType.PER_DAY, new BigDecimal("250.00"), LocalDate.of(2024, 3, 1), null, null),
                new EmployeeSalaryComponentDto(3L, 100L, 3L, "INCOME_TAX", "Income Tax", true,
                        ComponentType.DEDUCTION, CalcType.FLAT, new BigDecimal("1200.00"), LocalDate.of(2024, 3, 1), null, null)));
        try {
            CurrentUserHolder.set(new CurrentUser(1L, "hr.admin", "HR Admin", 1L, Set.of("HR"), Set.of("payroll.read")));
            String text = save(profileService().render(100L, null), "employee-profile-salary.pdf");
            assertThat(text).contains("SALARY INFORMATION", "Basic Salary", "Rs 65,292", "Rs 74,292", "Rs 1,200",
                    "SALARY COMPONENTS", "House Rent Allowance", "Meal Allowance", "Per day", "Income Tax");

            CurrentUserHolder.set(new CurrentUser(1L, "hr.clerk", "HR Clerk", 1L, Set.of("HR"), Set.of("employee.reports.run")));
            assertThat(text(profileService().render(100L, null))).doesNotContain("SALARY", "65,292");
        } finally {
            CurrentUserHolder.clear();
        }
    }

    @Test
    void employeeCardRendersOneCr80Page() throws Exception {
        EmployeeCardService svc = new EmployeeCardService(employees, departments, sections, designations,
                new EmployeeReportScope(employees, departments, sections, designations),
                letterSettings, jasper, mock(AuditEmitter.class), mock(TransactionTemplate.class));
        Department production = departments.findAll().get(0);
        Section stitching = sections.findAll().get(0);
        Designation operator = designations.findAll().get(0);
        when(departments.findById(1L)).thenReturn(Optional.of(production));
        when(sections.findById(10L)).thenReturn(Optional.of(stitching));
        when(designations.findById(20L)).thenReturn(Optional.of(operator));

        byte[] pdf = svc.render(100L, null);

        PdfReader reader = new PdfReader(pdf);
        assertThat(reader.getNumberOfPages()).isEqualTo(1);
        assertThat(reader.getPageSize(1).getWidth()).isEqualTo(243f);
        assertThat(reader.getPageSize(1).getHeight()).isEqualTo(153f);
        assertThat(save(pdf, "employee-card.pdf")).contains("FORWARD SPORTS (PVT.) LTD.", "Muhammad Ali",
                "S/O", "Muhammad Aslam", "Production", "Stitching", "MACHINE OPERATOR", "01-03-2024",
                "34603-1234567-1", "CARD NO.", "FS-2024-0007", "EMPLOYEE IDENTITY CARD");
        assertThatThrownBy(() -> svc.render(100L, 999L)).isInstanceOf(NotFoundError.class);
    }

    @Test
    void managerCannotPrintSomeoneElsesProfile() {
        assertThatThrownBy(() -> profileService().render(100L, 999L)).isInstanceOf(NotFoundError.class);
    }

    @Test
    void departmentStrengthGroupsAndTotals() throws Exception {
        DepartmentStrengthReportService svc = new DepartmentStrengthReportService(
                employees, departments, sections, designations, letterSettings, jasper, mock(AuditEmitter.class));

        byte[] pdf = svc.render(null);

        String text = save(pdf, "department-strength.pdf");
        assertThat(text).contains("Department Strength", "HUMAN RESOURCES", "Section: (Unassigned)", "HR Officer",
                "PRODUCTION", "Section: Stitching", "Machine Operator", "Production Total", "TOTAL STRENGTH");
        // Departments sort alphabetically: Human Resources before Production.
        assertThat(text.indexOf("HUMAN RESOURCES")).isLessThan(text.indexOf("PRODUCTION"));
        assertThat(new String(pdf, StandardCharsets.ISO_8859_1)).contains("/Helvetica-Bold");
    }

    @Test
    void departmentStrengthManagerSeesOnlyDirectReports() throws Exception {
        DepartmentStrengthReportService svc = new DepartmentStrengthReportService(
                employees, departments, sections, designations, letterSettings, jasper, mock(AuditEmitter.class));

        String text = text(svc.render(101L));

        assertThat(text).contains("direct reports only", "Machine Operator").doesNotContain("HR Officer");
    }

    private EmployeeProfileReportService profileService() {
        return new EmployeeProfileReportService(employees, departments, sections, designations, grades,
                mock(CityRepository.class), mock(QualificationRepository.class), mock(SkillRepository.class),
                contacts, contracts, history, mock(EmployeeQualificationRepository.class),
                mock(EmployeePriorExperienceRepository.class), mock(EmployeeCertificationRepository.class),
                mock(EmployeeSkillRepository.class), mock(EmployeeDocumentRepository.class),
                mock(DisciplinaryActionRepository.class), mock(EmployeeSuspensionRepository.class),
                mock(BelongingIssuanceRepository.class), payroll, letterSettings, jasper, mock(AuditEmitter.class));
    }

    private static String save(byte[] pdf, String name) throws Exception {
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        Path dir = Path.of("target", "report-previews");
        Files.createDirectories(dir);
        Files.write(dir.resolve(name), pdf);
        return text(pdf);
    }

    private static String text(byte[] pdf) throws Exception {
        PdfReader reader = new PdfReader(pdf);
        PdfTextExtractor extractor = new PdfTextExtractor(reader);
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= reader.getNumberOfPages(); i++) sb.append(extractor.getTextFromPage(i)).append('\n');
        return sb.toString().replaceAll("\\s+", " ");
    }

    private static Employee employee(long id, String code, String name, String gender,
                                     Long deptId, Long sectionId, Long desigId) {
        Employee e = new Employee();
        e.setId(id);
        e.setEmployeeCode(code);
        e.setFullName(name);
        e.setGender(gender);
        e.setDepartmentId(deptId);
        e.setSectionId(sectionId);
        e.setDesignationId(desigId);
        e.setEmploymentStatus(EmploymentStatus.ACTIVE);
        return e;
    }

    private static <T extends com.forward.hrms.commons.core.jpa.BaseLongIdEntity> T withId(T entity, long id) {
        entity.setId(id);
        return entity;
    }
}
