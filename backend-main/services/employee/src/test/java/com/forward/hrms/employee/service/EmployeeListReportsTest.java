package com.forward.hrms.employee.service;

import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.UnprocessableError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.reports.JasperRenderer;
import com.forward.hrms.commons.storage.ObjectStorage;
import com.forward.hrms.employee.config.DocumentProperties;
import com.forward.hrms.employee.dto.request.EmployeeReportFilter;
import com.forward.hrms.employee.entity.Department;
import com.forward.hrms.employee.entity.Designation;
import com.forward.hrms.employee.entity.Employee;
import com.forward.hrms.employee.entity.EmployeeExit;
import com.forward.hrms.employee.entity.EmployeeHistory;
import com.forward.hrms.employee.entity.EmployeeQualification;
import com.forward.hrms.employee.entity.Grade;
import com.forward.hrms.employee.entity.LetterSettings;
import com.forward.hrms.employee.entity.Qualification;
import com.forward.hrms.employee.entity.Section;
import com.forward.hrms.employee.enums.ChangeLogField;
import com.forward.hrms.employee.enums.DepartmentWiseGroupBy;
import com.forward.hrms.employee.enums.DesignationCategory;
import com.forward.hrms.employee.enums.EmployeeListColumns;
import com.forward.hrms.employee.enums.EmploymentStatus;
import com.forward.hrms.employee.enums.HistoryChangeReason;
import com.forward.hrms.employee.enums.ReportEmployeeStatus;
import com.forward.hrms.employee.repository.CityRepository;
import com.forward.hrms.employee.repository.DepartmentRepository;
import com.forward.hrms.employee.repository.DesignationRepository;
import com.forward.hrms.employee.repository.EmployeeExitRepository;
import com.forward.hrms.employee.repository.EmployeeHistoryRepository;
import com.forward.hrms.employee.repository.EmployeeQualificationRepository;
import com.forward.hrms.employee.repository.EmployeeRepository;
import com.forward.hrms.employee.repository.GradeRepository;
import com.forward.hrms.employee.repository.LetterSettingsRepository;
import com.forward.hrms.employee.repository.QualificationRepository;
import com.forward.hrms.employee.repository.SectionRepository;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Phase-1 list reports through the real POI writer and Jasper templates with
 * mocked repositories: the common filter, Employee List, Department Wise,
 * Change Log and bulk ID cards. Outputs land in {@code target/report-previews/}.
 */
class EmployeeListReportsTest {

    private final EmployeeRepository employees = mock(EmployeeRepository.class);
    private final DepartmentRepository departments = mock(DepartmentRepository.class);
    private final SectionRepository sections = mock(SectionRepository.class);
    private final DesignationRepository designations = mock(DesignationRepository.class);
    private final GradeRepository grades = mock(GradeRepository.class);
    private final CityRepository cities = mock(CityRepository.class);
    private final QualificationRepository qualificationCatalog = mock(QualificationRepository.class);
    private final EmployeeQualificationRepository qualifications = mock(EmployeeQualificationRepository.class);
    private final EmployeeHistoryRepository history = mock(EmployeeHistoryRepository.class);
    private final EmployeeExitRepository exits = mock(EmployeeExitRepository.class);
    private final com.forward.hrms.employee.repository.DisciplinaryActionRepository disciplinary =
            mock(com.forward.hrms.employee.repository.DisciplinaryActionRepository.class);
    private final com.forward.hrms.employee.repository.EmployeeDisabilityRepository employeeDisabilities =
            mock(com.forward.hrms.employee.repository.EmployeeDisabilityRepository.class);
    private final com.forward.hrms.employee.repository.DisabilityRepository disabilityCatalog =
            mock(com.forward.hrms.employee.repository.DisabilityRepository.class);
    private final LetterSettingsRepository settingsRepo = mock(LetterSettingsRepository.class);
    private final ObjectStorage storage = mock(ObjectStorage.class);
    private final JasperRenderer jasper = new JasperRenderer();

    private LetterSettingsService letterSettings;
    private EmployeeReportScope scope;
    private Employee ali, sara, bilal, left;

    @BeforeEach
    void setUp() {
        LetterSettings settings = new LetterSettings();
        settings.setCompanyLegalName("FORWARD SPORTS (PVT.) LTD.");
        settings.setCompanyAddress("WAZIRABAD ROAD SAHOWALA STOP, SIALKOT");
        settings.setCompanyPhone("052-3571800-5");
        when(settingsRepo.findFirstByOrderByIdAsc()).thenReturn(Optional.of(settings));
        when(storage.getObject(anyString(), anyString())).thenThrow(new RuntimeException("no such key"));
        letterSettings = new LetterSettingsService(settingsRepo, storage,
                new DocumentProperties("employee-docs", "employee-avatars", Duration.ofMinutes(10),
                        Duration.ofMinutes(10), Duration.ofDays(7), 10_000_000L, 2_000_000L));

        Department production = withId(new Department(), 1L);
        production.setName("Production");
        Department it = withId(new Department(), 2L);
        it.setName("Information Technology");
        Section stitching = withId(new Section(), 10L);
        stitching.setName("Stitching");
        Section sysdev = withId(new Section(), 11L);
        sysdev.setName("System Development");
        Designation operator = withId(new Designation(), 20L);
        operator.setTitle("Machine Operator");
        operator.setCategory(DesignationCategory.DIRECT);
        Designation officer = withId(new Designation(), 21L);
        officer.setTitle("Executive Officer");
        officer.setCategory(DesignationCategory.INDIRECT);
        Grade l2 = withId(new Grade(), 30L);
        l2.setCode("L-2");
        Grade m5 = withId(new Grade(), 31L);
        m5.setCode("M-5");
        Qualification bscs = withId(new Qualification(), 50L);
        bscs.setLevel("Graduation");
        bscs.setName("BS Computer Science");
        when(departments.findAll()).thenReturn(List.of(production, it));
        when(departments.findById(1L)).thenReturn(Optional.of(production));
        when(sections.findAll()).thenReturn(List.of(stitching, sysdev));
        when(designations.findAll()).thenReturn(List.of(operator, officer));
        when(grades.findAll()).thenReturn(List.of(l2, m5));
        when(qualificationCatalog.findAll()).thenReturn(List.of(bscs));

        ali = employee(100L, "11400", "Muhammad Ali", "MALE", 1L, 10L, 20L, 30L);
        ali.setFatherName("Muhammad Aslam");
        ali.setBloodGroup("b+");
        ali.setReligion("Islam");
        ali.setDateOfBirth(LocalDate.of(1990, 5, 14));
        ali.setJoiningDate(LocalDate.of(2015, 3, 1));
        sara = employee(101L, "11405", "Sara Khan", "FEMALE", 2L, 11L, 21L, 31L);
        sara.setDateOfBirth(LocalDate.of(2000, 9, 16));
        sara.setJoiningDate(LocalDate.of(2023, 10, 5));
        sara.setProfilePictureKey("employees/101/avatar.jpg");
        bilal = employee(102L, "9800", "Bilal Ahmed", "MALE", 1L, 10L, 20L, 30L);
        bilal.setJoiningDate(LocalDate.of(2010, 1, 1));
        left = employee(103L, "11500", "Usman Tariq", "MALE", 1L, 10L, 20L, 30L);
        left.setEmploymentStatus(EmploymentStatus.TERMINATED);
        when(employees.findAll()).thenReturn(List.of(ali, sara, bilal, left));

        EmployeeQualification q = new EmployeeQualification();
        q.setId(1L);
        q.setEmployeeId(101L);
        q.setQualificationId(50L);
        q.setYearOfCompletion(2022);
        when(qualifications.findAll()).thenReturn(List.of(q));

        // Ali: onboarded L-2 in Stitching → transferred to IT in 2025 → back to Production, graded M-5.
        when(history.findAll()).thenReturn(List.of(
                history(1L, 100L, LocalDate.of(2015, 3, 1), 1L, 10L, 20L, 30L, HistoryChangeReason.ONBOARD),
                history(2L, 100L, LocalDate.of(2025, 2, 1), 2L, 11L, 20L, 30L, HistoryChangeReason.TRANSFER),
                history(3L, 100L, LocalDate.of(2025, 8, 1), 1L, 10L, 20L, 30L, HistoryChangeReason.TRANSFER)));

        EmployeeExit exit = new EmployeeExit();
        exit.setId(1L);
        exit.setEmployeeId(103L);
        exit.setLastWorkingDay(LocalDate.of(2026, 6, 30));
        when(exits.findAll()).thenReturn(List.of(exit));

        scope = new EmployeeReportScope(employees, departments, sections, designations);
        CurrentUserHolder.set(new CurrentUser(1L, "hr.admin", "HR Admin", 1L, Set.of("HR"), Set.of("employee.reports.run")));
    }

    @AfterEach
    void tearDown() {
        CurrentUserHolder.clear();
    }

    // ---------- filter ----------

    @Test
    void filterDefaultsToActiveAndOrdersByCardNumerically() {
        assertThat(scope.select(null, null)).extracting(Employee::getEmployeeCode).containsExactly("9800", "11400", "11405");
        assertThat(scope.select(filter(ReportEmployeeStatus.INACTIVE, null, null, null, null), null))
                .extracting(Employee::getEmployeeCode).containsExactly("11500");
    }

    @Test
    void cardRangeIsNumericAndCardListIsExact() {
        assertThat(scope.select(filter(null, null, "10000", "11402", null), null))
                .extracting(Employee::getEmployeeCode).containsExactly("11400");
        assertThat(scope.select(filter(ReportEmployeeStatus.ALL, null, null, null, "11405, 11500 ,x"), null))
                .extracting(Employee::getEmployeeCode).containsExactly("11405", "11500");
        assertThat(scope.select(filter(null, 1L, null, null, null), 999L)).isEmpty();
    }

    // ---------- employee list ----------

    @Test
    void employeeListBasicHasOneRowPerMatchAndTheFilterCaption() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        listService().writeXlsx(out, filter(null, 1L, null, null, null), EmployeeListColumns.BASIC, false, null);
        save(out.toByteArray(), "employee-list.xlsx");

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
            Sheet sheet = wb.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("FORWARD SPORTS (PVT.) LTD.");
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("Employee List");
            assertThat(sheet.getRow(2).getCell(0).getStringCellValue()).contains("Status: Active", "Department: Production");
            List<String> header = cells(sheet.getRow(5));
            assertThat(header).startsWith("#", "Card No", "Name").contains("Grade Effective", "Leave Date").hasSize(18);
            assertThat(cells(sheet.getRow(6))).startsWith("1", "9800", "Bilal Ahmed");
            List<String> aliRow = cells(sheet.getRow(7));
            assertThat(aliRow).contains("11400", "Muhammad Ali", "Production", "Stitching", "Machine Operator",
                    "Direct", "L-2", "01-Mar-2015", "b+", "Active");
            assertThat(cells(sheet.getRow(8))).contains("Total", "2 employee(s)");
        }
    }

    @Test
    void employeeListFullAddsColumnsAndMissingPhotoFilters() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        listService().writeXlsx(out, null, EmployeeListColumns.FULL, true, null);
        save(out.toByteArray(), "employee-list-full.xlsx");

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
            Sheet sheet = wb.getSheetAt(0);
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("Employee List — All Information — Missing Photo");
            assertThat(cells(sheet.getRow(5))).hasSize(37).contains("Age", "Total Service", "Photo");
            List<String> codes = new ArrayList<>();
            for (int r = 6; r <= sheet.getLastRowNum(); r++) codes.add(cells(sheet.getRow(r)).get(1));
            assertThat(codes).contains("9800", "11400").doesNotContain("11405");
        }
    }

    @Test
    void gradeEffectiveIsStartOfTheCurrentGradeRun() {
        Employee e = new Employee();
        e.setGradeId(31L);
        List<EmployeeHistory> rows = List.of(
                history(1L, 1L, LocalDate.of(2020, 1, 1), 1L, null, null, 30L, HistoryChangeReason.ONBOARD),
                history(2L, 1L, LocalDate.of(2023, 7, 1), 1L, null, null, 31L, HistoryChangeReason.PROMOTION),
                history(3L, 1L, LocalDate.of(2024, 7, 1), 2L, null, null, 31L, HistoryChangeReason.TRANSFER));
        assertThat(EmployeeListReportService.gradeEffective(e, rows)).isEqualTo(LocalDate.of(2023, 7, 1));
    }

    // ---------- department wise ----------

    @Test
    void departmentWiseBreaksDepartmentsDownWithCompanyBlock() throws Exception {
        DepartmentWiseReportService svc = new DepartmentWiseReportService(scope, departments, designations, grades,
                qualificationCatalog, qualifications, letterSettings, jasper, mock(AuditEmitter.class));

        String text = savePdf(svc.render(null, DepartmentWiseGroupBy.GRADE, null), "department-wise.pdf");

        assertThat(text).contains("FORWARD SPORTS (PVT.) LTD.", "Department Wise — Grade", "PRODUCTION",
                "INFORMATION TECHNOLOGY", "ALL DEPARTMENTS", "L-2", "M-5", "100.0%", "66.7%", "TOTAL EMPLOYEES");
        assertThat(savePdf(svc.render(null, DepartmentWiseGroupBy.AGE_BAND, null), "department-wise-age.pdf"))
                .contains("Department Wise — Age", "20-29", "30-39", "(Not set)");
    }

    // ---------- change log ----------

    @Test
    void changeLogListsEachFieldChangeWithOldAndNewValue() throws Exception {
        ChangeLogReportService svc = new ChangeLogReportService(scope, history, departments, sections, designations,
                grades, letterSettings, mock(AuditEmitter.class));
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        svc.writeXlsx(out, null, null, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31), null);
        save(out.toByteArray(), "change-log.xlsx");

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
            Sheet sheet = wb.getSheetAt(0);
            assertThat(cells(sheet.getRow(6))).contains("01-Feb-2025", "11400", "Department", "Production", "Information Technology", "Transfer");
            assertThat(cells(sheet.getRow(7))).contains("Section", "Stitching", "System Development");
            assertThat(cells(sheet.getRow(8))).contains("01-Aug-2025", "Department", "Information Technology", "Production");
            assertThat(cells(sheet.getRow(10))).contains("4 change(s)");
        }

        ByteArrayOutputStream gradeOnly = new ByteArrayOutputStream();
        svc.writeXlsx(gradeOnly, null, ChangeLogField.GRADE, null, null, null);
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(gradeOnly.toByteArray()))) {
            assertThat(cells(wb.getSheetAt(0).getRow(6))).contains("0 change(s)");
        }
        assertThatThrownBy(() -> svc.writeXlsx(new ByteArrayOutputStream(), null, null,
                LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 1), null)).isInstanceOf(UnprocessableError.class);
    }

    // ---------- bulk cards ----------

    @Test
    void bulkCardsPrintOnePagePerEmployeeAndRefuseEmptyRuns() throws Exception {
        EmployeeCardService svc = new EmployeeCardService(employees, departments, sections, designations, scope,
                letterSettings, jasper, mock(AuditEmitter.class), mock(TransactionTemplate.class));

        byte[] pdf = svc.renderMany(filter(null, 1L, null, null, null), null);
        savePdf(pdf, "employee-cards.pdf");

        PdfReader reader = new PdfReader(pdf);
        assertThat(reader.getNumberOfPages()).isEqualTo(2);
        assertThat(new PdfTextExtractor(reader).getTextFromPage(1)).contains("Bilal Ahmed", "9800");
        assertThat(new PdfTextExtractor(reader).getTextFromPage(2)).contains("Muhammad Ali", "11400");
        assertThatThrownBy(() -> svc.renderMany(filter(null, null, "1", "2", null), null))
                .isInstanceOf(UnprocessableError.class);
    }

    // ---------- confirmation / relieving letters ----------

    @Test
    void confirmationLettersForAMonthMergeOnePagePerEmployee() throws Exception {
        ali.setConfirmationDate(LocalDate.of(2026, 9, 1));
        sara.setConfirmationDate(LocalDate.of(2026, 9, 20));
        bilal.setConfirmationDate(LocalDate.of(2026, 8, 31));
        EmploymentLetterService svc = letters();

        byte[] pdf = svc.confirmationLetters(java.time.YearMonth.of(2026, 9), null, null);
        savePdf(pdf, "confirmation-letters.pdf");

        PdfReader reader = new PdfReader(pdf);
        assertThat(reader.getNumberOfPages()).isEqualTo(2);
        String first = new PdfTextExtractor(reader).getTextFromPage(1).replaceAll("\\s+", " ");
        assertThat(first).contains("CONFIRMATION LETTER", "Dear Mr. Muhammad Ali",
                "services as Machine Operator in the Production department are hereby confirmed with effect from 01 September 2026");
        assertThat(new PdfTextExtractor(reader).getTextFromPage(2)).contains("Ms. Sara Khan");
        assertThatThrownBy(() -> svc.confirmationLetters(java.time.YearMonth.of(2026, 1), null, null))
                .isInstanceOf(UnprocessableError.class);
    }

    @Test
    void singleConfirmationNeedsAConfirmationDate() {
        when(employees.findById(102L)).thenReturn(Optional.of(bilal));
        assertThatThrownBy(() -> letters().confirmationLetter(102L, null))
                .isInstanceOfSatisfying(UnprocessableError.class,
                        ex -> assertThat(ex.extensions()).containsEntry("missingFields", List.of("confirmationDate")));
    }

    @Test
    void relievingLetterUsesTheLastExitAndRefusesCurrentStaff() throws Exception {
        left.setJoiningDate(LocalDate.of(2020, 1, 6));
        left.setFatherName("Tariq Mehmood");
        EmployeeExit exit = new EmployeeExit();
        exit.setEmployeeId(103L);
        exit.setExitType(com.forward.hrms.employee.enums.ExitType.RESIGNATION);
        exit.setLastWorkingDay(LocalDate.of(2026, 6, 30));
        when(exits.findFirstByEmployeeIdOrderByIdDesc(103L)).thenReturn(Optional.of(exit));
        when(employees.findById(103L)).thenReturn(Optional.of(left));
        when(employees.findById(100L)).thenReturn(Optional.of(ali));

        String text = savePdf(letters().relievingLetter(103L, null), "relieving-letter.pdf");

        assertThat(text).contains("RELIEVING LETTER", "Mr. Usman Tariq S/O Tariq Mehmood, Card No. 11500",
                "from 06 January 2020 to 30 June 2026", "upon acceptance of his resignation");
        assertThatThrownBy(() -> letters().relievingLetter(100L, null)).isInstanceOf(UnprocessableError.class);
    }

    // ---------- character certificate ----------

    @Test
    void characterCertificateIsRefusedWhileADisciplinaryActionStands() throws Exception {
        ali.setCnic("34603-1234567-1");
        when(employees.findById(100L)).thenReturn(Optional.of(ali));
        com.forward.hrms.employee.entity.DisciplinaryAction warning = new com.forward.hrms.employee.entity.DisciplinaryAction();
        warning.setId(9L);
        warning.setEmployeeId(100L);
        warning.setDecision(com.forward.hrms.employee.enums.DisciplinaryDecision.WITHDRAWN);
        when(disciplinary.listForEmployee(100L, null, null, null, null)).thenReturn(List.of(warning));

        String text = savePdf(letters().characterCertificate(100L, null), "character-certificate.pdf");
        assertThat(text).contains("CHARACTER CERTIFICATE", "Mr. Muhammad Ali S/O Muhammad Aslam, Card No. 11400, CNIC 34603-1234567-1",
                "is working with FORWARD SPORTS (PVT.) LTD. as Machine Operator", "since 01 March 2015",
                "his conduct and character remained good", "issued at his request");

        warning.setDecision(com.forward.hrms.employee.enums.DisciplinaryDecision.UPHELD);
        assertThatThrownBy(() -> letters().characterCertificate(100L, null))
                .isInstanceOfSatisfying(UnprocessableError.class,
                        ex -> assertThat(ex.extensions()).containsEntry("disciplinaryActions", List.of(9L)));
    }

    // ---------- registers ----------

    @Test
    void disabilityListShowsEachRecordWithCertificateStatus() throws Exception {
        com.forward.hrms.employee.entity.Disability hearing = withId(new com.forward.hrms.employee.entity.Disability(), 5L);
        hearing.setName("Hearing Impairment");
        when(disabilityCatalog.findAll()).thenReturn(List.of(hearing));
        com.forward.hrms.employee.entity.EmployeeDisability d = new com.forward.hrms.employee.entity.EmployeeDisability();
        d.setId(1L);
        d.setEmployeeId(101L);
        d.setDisabilityId(5L);
        d.setSeverity(com.forward.hrms.employee.enums.DisabilitySeverity.MODERATE);
        d.setDocumentKey("employees/101/disability.pdf");
        when(employeeDisabilities.findAll()).thenReturn(List.of(d));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        registers().writeDisabilitiesXlsx(out, null, null);
        save(out.toByteArray(), "disability-list.xlsx");

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
            Sheet sheet = wb.getSheetAt(0);
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("Disability List");
            assertThat(cells(sheet.getRow(6))).contains("11405", "Sara Khan", "Female", "Information Technology",
                    "Hearing Impairment", "Moderate", "On file", "Active");
            assertThat(cells(sheet.getRow(7))).contains("1 record(s), 1 employee(s)");
        }
    }

    @Test
    void rejoiningListPairsReinstatementWithTheExitItReversed() throws Exception {
        EmployeeHistory back = history(4L, 100L, LocalDate.of(2026, 3, 1), 1L, 10L, 20L, 30L, HistoryChangeReason.REINSTATED);
        back.setNotes("Rejoined after family leave");
        when(history.findAll()).thenReturn(List.of(back));
        EmployeeExit gone = new EmployeeExit();
        gone.setId(2L);
        gone.setEmployeeId(100L);
        gone.setExitType(com.forward.hrms.employee.enums.ExitType.RESIGNATION);
        gone.setLastWorkingDay(LocalDate.of(2026, 1, 31));
        when(exits.findAll()).thenReturn(List.of(gone));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        registers().writeRejoiningsXlsx(out, null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), null);
        save(out.toByteArray(), "rejoining-list.xlsx");

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
            assertThat(cells(wb.getSheetAt(0).getRow(6))).containsExactly("1", "01-Mar-2026", "11400", "Muhammad Ali",
                    "Production", "Machine Operator", "01-Mar-2015", "31-Jan-2026", "Resignation", "29",
                    "Rejoined after family leave");
        }
    }

    // ---------- fixtures ----------

    private EmployeeRegistersReportService registers() {
        return new EmployeeRegistersReportService(scope, employeeDisabilities, disabilityCatalog, history, exits,
                departments, sections, designations, letterSettings, mock(AuditEmitter.class));
    }

    private EmploymentLetterService letters() {
        return new EmploymentLetterService(employees, exits, departments, designations, disciplinary, scope, letterSettings,
                jasper, mock(AuditEmitter.class), mock(TransactionTemplate.class));
    }

    private EmployeeListReportService listService() {
        return new EmployeeListReportService(scope, departments, sections, designations, grades, cities,
                qualificationCatalog, qualifications, history, exits, letterSettings, mock(AuditEmitter.class));
    }

    private static EmployeeReportFilter filter(ReportEmployeeStatus status, Long departmentId, String from, String to, String cards) {
        return new EmployeeReportFilter(status, departmentId, null, null, from, to, cards);
    }

    private static Employee employee(long id, String code, String name, String gender, Long dept, Long section,
                                     Long desig, Long grade) {
        Employee e = new Employee();
        e.setId(id);
        e.setEmployeeCode(code);
        e.setFullName(name);
        e.setGender(gender);
        e.setDepartmentId(dept);
        e.setSectionId(section);
        e.setDesignationId(desig);
        e.setGradeId(grade);
        return e;
    }

    private static EmployeeHistory history(long id, long employeeId, LocalDate from, Long dept, Long section,
                                           Long desig, Long grade, HistoryChangeReason reason) {
        EmployeeHistory h = new EmployeeHistory();
        h.setId(id);
        h.setEmployeeId(employeeId);
        h.setEffectiveFrom(from);
        h.setDepartmentId(dept);
        h.setSectionId(section);
        h.setDesignationId(desig);
        h.setGradeId(grade);
        h.setChangeReason(reason);
        h.setCreatedBy("hr.admin");
        return h;
    }

    private static List<String> cells(Row row) {
        List<String> out = new ArrayList<>();
        if (row == null) return out;
        for (int c = 0; c < row.getLastCellNum(); c++) {
            var cell = row.getCell(c);
            if (cell == null) out.add("");
            else if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC) {
                double d = cell.getNumericCellValue();
                out.add(d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d));
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

    private static <T extends com.forward.hrms.commons.core.jpa.BaseLongIdEntity> T withId(T entity, long id) {
        entity.setId(id);
        return entity;
    }
}
