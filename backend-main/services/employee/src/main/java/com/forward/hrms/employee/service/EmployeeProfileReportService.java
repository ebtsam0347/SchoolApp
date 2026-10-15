package com.forward.hrms.employee.service;

import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.reports.JasperRenderer;
import com.forward.hrms.employee.client.PayrollClient;
import com.forward.hrms.employee.entity.BelongingIssuance;
import com.forward.hrms.employee.entity.City;
import com.forward.hrms.employee.entity.Department;
import com.forward.hrms.employee.entity.Designation;
import com.forward.hrms.employee.entity.DisciplinaryAction;
import com.forward.hrms.employee.entity.EmergencyContact;
import com.forward.hrms.employee.entity.Employee;
import com.forward.hrms.employee.entity.EmployeeCertification;
import com.forward.hrms.employee.entity.EmployeeDocument;
import com.forward.hrms.employee.entity.EmployeeHistory;
import com.forward.hrms.employee.entity.EmployeePriorExperience;
import com.forward.hrms.employee.entity.EmployeeQualification;
import com.forward.hrms.employee.entity.EmployeeSkill;
import com.forward.hrms.employee.entity.EmployeeSuspension;
import com.forward.hrms.employee.entity.EmploymentContract;
import com.forward.hrms.employee.entity.Grade;
import com.forward.hrms.employee.entity.LetterSettings;
import com.forward.hrms.employee.entity.Qualification;
import com.forward.hrms.employee.entity.Section;
import com.forward.hrms.employee.entity.Skill;
import com.forward.hrms.employee.enums.EmployeeErrorCode;
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
import com.forward.hrms.employee.repository.QualificationRepository;
import com.forward.hrms.employee.repository.SectionRepository;
import com.forward.hrms.employee.repository.SkillRepository;
import com.forward.hrms.payroll.dto.response.EmployeeSalaryComponentDto;
import com.forward.hrms.payroll.dto.response.EmployeeSalaryDto;
import com.forward.hrms.payroll.enums.CalcType;
import com.forward.hrms.payroll.enums.ComponentType;

import lombok.RequiredArgsConstructor;

import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Employee Profile — the printable personnel file for one employee
 * ({@code jasper/employee-profile.jrxml}). Every section of the employee
 * record (personal, contact, employment, salary, statutory numbers, emergency
 * contacts, contracts, career history, qualifications, prior experience,
 * certifications, skills, documents on file, disciplinary, suspensions,
 * belongings) flattened into one row list. Company name/address come from
 * {@code letter_settings}, the same letterhead the HR letters use. Salary
 * (basic + current components from payroll) prints only for callers holding
 * {@code payroll.read}; for everyone else the section is left out.
 *
 * <p>Row shape: every row carries {@code section} (group key) and
 * {@code kind}, which picks the detail band:
 * <ul>
 *   <li>{@code kv} — two label/value pairs side by side ({@code l1,v1,l2,v2}).</li>
 *   <li>{@code wide} — one label/value spanning the page (addresses).</li>
 *   <li>{@code table} — five cells {@code c1..c5} under the section's
 *       column headings {@code h1..h5} (repeated on every row of the section
 *       so the group header can read them from the first one).</li>
 *   <li>{@code empty} — "No records." placeholder for a table section.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class EmployeeProfileReportService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MMM-yyyy");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm");

    private final EmployeeRepository employees;
    private final DepartmentRepository departments;
    private final SectionRepository sections;
    private final DesignationRepository designations;
    private final GradeRepository grades;
    private final CityRepository cities;
    private final QualificationRepository qualificationCatalog;
    private final SkillRepository skillCatalog;
    private final EmergencyContactRepository emergencyContacts;
    private final EmploymentContractRepository contracts;
    private final EmployeeHistoryRepository history;
    private final EmployeeQualificationRepository qualifications;
    private final EmployeePriorExperienceRepository priorExperience;
    private final EmployeeCertificationRepository certifications;
    private final EmployeeSkillRepository skills;
    private final EmployeeDocumentRepository documents;
    private final DisciplinaryActionRepository disciplinary;
    private final EmployeeSuspensionRepository suspensions;
    private final BelongingIssuanceRepository belongings;
    private final PayrollClient payroll;
    private final LetterSettingsService letterSettings;
    private final JasperRenderer jasper;
    private final AuditEmitter audit;

    /**
     * @param restrictToManagerId when non-null, the caller is a manager and
     *        may only print a direct report's profile — anyone else is a 404
     *        (same shape as a missing employee, so ids can't be probed).
     */
    @Transactional
    public byte[] render(long employeeId, Long restrictToManagerId) {
        Employee e = employees.findById(employeeId)
                .filter(emp -> restrictToManagerId == null || restrictToManagerId.equals(emp.getManagerId()))
                .orElseThrow(() -> new NotFoundError(EmployeeErrorCode.EMPLOYEE_NOT_FOUND, "employee not found"));

        Map<Long, Department> deptById = byId(departments.findAll(), Department::getId);
        Map<Long, Section> sectionById = byId(sections.findAll(), Section::getId);
        Map<Long, Designation> desigById = byId(designations.findAll(), Designation::getId);
        Map<Long, Grade> gradeById = byId(grades.findAll(), Grade::getId);

        List<Map<String, ?>> rows = new ArrayList<>();
        personal(rows, e);
        contact(rows, e);
        employment(rows, e, deptById, sectionById, desigById, gradeById);
        CurrentUser actor = CurrentUserHolder.get().orElse(null);
        if (actor != null && actor.hasPermission(AppointmentLetterService.PAYROLL_READ)) salary(rows, e.getId());
        statutory(rows, e);
        emergencyContacts(rows, e.getId());
        contracts(rows, e.getId());
        careerHistory(rows, e.getId(), deptById, desigById, gradeById);
        qualifications(rows, e.getId());
        priorExperience(rows, e.getId());
        certifications(rows, e.getId());
        skills(rows, e.getId());
        documents(rows, e.getId());
        disciplinary(rows, e.getId());
        suspensions(rows, e.getId());
        belongings(rows, e.getId());

        LetterSettings settings = letterSettings.current();
        Map<String, Object> params = new HashMap<>();
        params.put("companyName", settings.getCompanyLegalName());
        params.put("companyAddress", settings.getCompanyAddress());
        params.put("reportTitle", "Employee Profile");
        params.put("employeeName", e.getFullName());
        params.put("employeeCode", e.getEmployeeCode());
        params.put("designation", desigName(desigById, e.getDesignationId()));
        params.put("department", deptName(deptById, e.getDepartmentId()));
        params.put("employmentStatus", pretty(e.getEmploymentStatus()));
        params.put("photo", letterSettings.employeePhoto(e.getProfilePictureKey()));
        params.put("generatedAt", STAMP.format(LocalDateTime.now()));
        params.put("generatedBy", actor == null ? CurrentUser.SYSTEM_USERNAME : actor.username());

        byte[] pdf = jasper.renderPdf("/jasper/employee-profile.jrxml", params, new JRMapCollectionDataSource(rows));
        audit.emit("report", String.valueOf(employeeId), "employee-profile.run",
                null, Map.of("employeeId", employeeId), true);
        return pdf;
    }

    // ---------- key/value sections ----------

    private static void personal(List<Map<String, ?>> rows, Employee e) {
        String s = "Personal Information";
        kv(rows, s, "Full Name", e.getFullName(), "Father's Name", e.getFatherName());
        kv(rows, s, "CNIC", e.getCnic(), "Date of Birth", dateWithAge(e.getDateOfBirth()));
        kv(rows, s, "CNIC Issue Date", date(e.getCnicIssueDate()), "CNIC Expiry Date", date(e.getCnicExpiryDate()));
        kv(rows, s, "Gender", pretty(e.getGender()), "Marital Status", pretty(e.getMaritalStatus()));
        kv(rows, s, "Mother's Name", e.getMotherName(), "Spouse Name", e.getSpouseName());
        kv(rows, s, "Religion", e.getReligion(), "Blood Group", e.getBloodGroup());
    }

    private void contact(List<Map<String, ?>> rows, Employee e) {
        String s = "Contact Details";
        String city = e.getCityId() == null ? null : cities.findById(e.getCityId()).map(City::getName).orElse(null);
        kv(rows, s, "Phone", e.getPhone(), "City", city);
        kv(rows, s, "Personal Email", e.getPersonalEmail(), "Work Email", e.getWorkEmail());
        wide(rows, s, "Current Address", e.getCurrentAddress());
        wide(rows, s, "Permanent Address", e.getPermanentAddress());
    }

    private void employment(List<Map<String, ?>> rows, Employee e,
                            Map<Long, Department> deptById, Map<Long, Section> sectionById,
                            Map<Long, Designation> desigById, Map<Long, Grade> gradeById) {
        String s = "Employment";
        Section section = e.getSectionId() == null ? null : sectionById.get(e.getSectionId());
        Grade grade = e.getGradeId() == null ? null : gradeById.get(e.getGradeId());
        String manager = e.getManagerId() == null ? null
                : employees.findById(e.getManagerId())
                        .map(m -> m.getFullName() + " (" + m.getEmployeeCode() + ")").orElse(null);
        kv(rows, s, "Employee Code", e.getEmployeeCode(), "Employment Status", pretty(e.getEmploymentStatus()));
        kv(rows, s, "Joining Date", date(e.getJoiningDate()), "Confirmation Date", date(e.getConfirmationDate()));
        kv(rows, s, "Length of Service", serviceLength(e.getJoiningDate()), "Employment Type", pretty(e.getEmploymentType()));
        kv(rows, s, "Department", deptName(deptById, e.getDepartmentId()), "Section", section == null ? null : section.getName());
        kv(rows, s, "Designation", desigName(desigById, e.getDesignationId()),
                "Grade", grade == null ? null : grade.getCode() + " — " + grade.getName());
        kv(rows, s, "Reporting To", manager, "Reference By", e.getReferenceBy());
    }

    /** Basic + fixed monthly totals as key/values, then every current component as a table. */
    private void salary(List<Map<String, ?>> rows, long employeeId) {
        String s = "Salary Information";
        EmployeeSalaryDto current = payroll.currentSalary(employeeId).orElse(null);
        List<EmployeeSalaryComponentDto> lines = payroll.currentComponents(employeeId);
        BigDecimal basic = current == null ? null : current.basic();
        BigDecimal earnings = fixedTotal(lines, ComponentType.EARNING);
        BigDecimal deductions = fixedTotal(lines, ComponentType.DEDUCTION);
        kv(rows, s, "Basic Salary", amount(basic), "Effective From", current == null ? null : date(current.effectiveFrom()));
        kv(rows, s, "Monthly Gross (fixed)", basic == null ? null : amount(basic.add(earnings)),
                "Monthly Deductions (fixed)", amount(deductions));

        Table t = new Table(rows, "Salary Components", "Component", "Type", "Calculation", "Amount", "Effective From");
        for (EmployeeSalaryComponentDto c : lines) {
            t.add(c.componentName(), pretty(c.type()), c.calcType() == CalcType.PER_DAY ? "Per day" : "Monthly",
                    amount(c.amount()), date(c.effectiveFrom()));
        }
        t.close();
    }

    private static BigDecimal fixedTotal(List<EmployeeSalaryComponentDto> lines, ComponentType type) {
        return lines.stream()
                .filter(c -> c.type() == type && c.calcType() != CalcType.PER_DAY && c.amount() != null)
                .map(EmployeeSalaryComponentDto::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static void statutory(List<Map<String, ?>> rows, Employee e) {
        String s = "Statutory Numbers";
        kv(rows, s, "NTN", e.getNtn(), "EOBI Number", e.getEobiNumber());
        kv(rows, s, "Social Security (PESSI) No.", e.getSscNumber(), "Insurance Number", e.getInsuranceNumber());
    }

    // ---------- table sections ----------

    private void emergencyContacts(List<Map<String, ?>> rows, long employeeId) {
        Table t = new Table(rows, "Emergency Contacts", "Name", "Relation", "Phone", "Primary", "");
        for (EmergencyContact c : emergencyContacts.findAllByEmployeeIdOrderByPrimaryDescIdAsc(employeeId)) {
            t.add(c.getName(), c.getRelation(), c.getPhone(), c.isPrimary() ? "Yes" : "No", null);
        }
        t.close();
    }

    private void contracts(List<Map<String, ?>> rows, long employeeId) {
        Table t = new Table(rows, "Employment Contracts", "Type", "Start Date", "End Date", "Probation Days", "Status");
        for (EmploymentContract c : contracts.findAllByEmployeeIdOrderByStartDateDesc(employeeId)) {
            t.add(pretty(c.getContractType()), date(c.getStartDate()), date(c.getEndDate()),
                    c.getProbationDays() == null ? null : c.getProbationDays() + (c.isExtended() ? " (extended)" : ""),
                    pretty(c.getStatus()));
        }
        t.close();
    }

    private void careerHistory(List<Map<String, ?>> rows, long employeeId, Map<Long, Department> deptById,
                               Map<Long, Designation> desigById, Map<Long, Grade> gradeById) {
        Table t = new Table(rows, "Career History", "Period", "Department", "Designation", "Grade", "Reason");
        for (EmployeeHistory h : history.findAllByEmployeeIdOrderByEffectiveFromDesc(employeeId, Pageable.unpaged())) {
            Grade g = h.getGradeId() == null ? null : gradeById.get(h.getGradeId());
            // Plain "to": the PDF's base-14 font has no arrow glyph.
            t.add(date(h.getEffectiveFrom()) + " to " + (h.getEffectiveTo() == null ? "Present" : date(h.getEffectiveTo())),
                    deptName(deptById, h.getDepartmentId()), desigName(desigById, h.getDesignationId()),
                    g == null ? null : g.getCode(), pretty(h.getChangeReason()));
        }
        t.close();
    }

    private void qualifications(List<Map<String, ?>> rows, long employeeId) {
        Map<Long, Qualification> catalog = byId(qualificationCatalog.findAll(), Qualification::getId);
        Table t = new Table(rows, "Qualifications", "Qualification", "Institution", "Board / University", "Year", "Grade");
        for (EmployeeQualification q : qualifications.findAllByEmployeeIdOrderByYearOfCompletionDescIdAsc(employeeId)) {
            Qualification c = q.getQualificationId() == null ? null : catalog.get(q.getQualificationId());
            t.add(c == null ? null : c.getName(), q.getInstitution(), q.getBoardOrUniversity(),
                    q.getYearOfCompletion() == null ? null : String.valueOf(q.getYearOfCompletion()), q.getGrade());
        }
        t.close();
    }

    private void priorExperience(List<Map<String, ?>> rows, long employeeId) {
        Table t = new Table(rows, "Previous Experience", "Employer", "Designation", "From", "To", "Reason for Leaving");
        for (EmployeePriorExperience x : priorExperience.findAllByEmployeeIdOrderByEndDateDescIdAsc(employeeId)) {
            t.add(x.getEmployerName(), x.getDesignation(), date(x.getStartDate()), date(x.getEndDate()),
                    x.getReasonForLeaving());
        }
        t.close();
    }

    private void certifications(List<Map<String, ?>> rows, long employeeId) {
        Table t = new Table(rows, "Certifications", "Certification", "Issuing Body", "Issued", "Expires", "Credential ID");
        for (EmployeeCertification c : certifications.findAllByEmployeeIdOrderByIssueDateDescIdAsc(employeeId)) {
            t.add(c.getName(), c.getIssuingBody(), date(c.getIssueDate()), date(c.getExpiryDate()), c.getCredentialId());
        }
        t.close();
    }

    private void skills(List<Map<String, ?>> rows, long employeeId) {
        Map<Long, Skill> catalog = byId(skillCatalog.findAll(), Skill::getId);
        Table t = new Table(rows, "Skills", "Skill", "Category", "Proficiency", "Years", "Notes");
        for (EmployeeSkill k : skills.findAllByEmployeeIdOrderByIdAsc(employeeId)) {
            Skill c = k.getSkillId() == null ? null : catalog.get(k.getSkillId());
            t.add(c == null ? null : c.getName(), c == null ? null : c.getCategory(), pretty(k.getProficiency()),
                    k.getYears() == null ? null : k.getYears().stripTrailingZeros().toPlainString(), k.getNotes());
        }
        t.close();
    }

    private void documents(List<Map<String, ?>> rows, long employeeId) {
        Table t = new Table(rows, "Documents on File", "Type", "Title", "Uploaded On", "Uploaded By", "Size");
        for (EmployeeDocument d : documents.findAllByEmployeeIdOrderByIdDesc(employeeId)) {
            t.add(pretty(d.getDocType()), d.getTitle(),
                    d.getCreatedAt() == null ? null : date(d.getCreatedAt().toLocalDate()),
                    d.getCreatedBy(), size(d.getSizeBytes()));
        }
        t.close();
    }

    private void disciplinary(List<Map<String, ?>> rows, long employeeId) {
        Table t = new Table(rows, "Disciplinary Record", "Issued On", "Level", "Category", "Decision", "Status");
        for (DisciplinaryAction d : disciplinary.listForEmployee(employeeId, null, null, null, null)) {
            t.add(date(d.getIssuedOn()), pretty(d.getLevel()), pretty(d.getCategory()),
                    pretty(d.getDecision()), pretty(d.getStatus()));
        }
        t.close();
    }

    private void suspensions(List<Map<String, ?>> rows, long employeeId) {
        Table t = new Table(rows, "Suspensions", "From", "To", "With Pay", "Lifted On", "Reason");
        for (EmployeeSuspension x : suspensions.findAllByEmployeeIdOrderByStartDateDesc(employeeId)) {
            t.add(date(x.getStartDate()), date(x.getEndDate()), x.isWithPay() ? "Yes" : "No",
                    date(x.getLiftedOn()), x.getReason());
        }
        t.close();
    }

    private void belongings(List<Map<String, ?>> rows, long employeeId) {
        Table t = new Table(rows, "Company Belongings", "Item", "Model", "Issued On", "Condition", "Clearance");
        for (BelongingIssuance b : belongings.findByEmployeeIdOrderByIssuedDateDesc(employeeId)) {
            t.add(b.getBelongingMaster() == null ? null : b.getBelongingMaster().getBelongingName(),
                    b.getAssetModel(), date(b.getIssuedDate()), pretty(b.getCondition()),
                    pretty(b.getClearanceStatus()));
        }
        t.close();
    }

    // ---------- row builders ----------

    private static void kv(List<Map<String, ?>> rows, String section, String l1, String v1, String l2, String v2) {
        Map<String, Object> m = row(section, "kv");
        m.put("l1", l1);
        m.put("v1", dash(v1));
        m.put("l2", l2);
        m.put("v2", l2 == null ? null : dash(v2));
        rows.add(m);
    }

    private static void wide(List<Map<String, ?>> rows, String section, String label, String value) {
        Map<String, Object> m = row(section, "wide");
        m.put("l1", label);
        m.put("v1", dash(value));
        rows.add(m);
    }

    private static Map<String, Object> row(String section, String kind) {
        Map<String, Object> m = new HashMap<>();
        m.put("section", section);
        m.put("kind", kind);
        return m;
    }

    /** Collects one table section's rows; {@link #close} adds the "No records." row when empty. */
    private static final class Table {
        private final List<Map<String, ?>> rows;
        private final String section;
        private final String[] headings;
        private int count;

        Table(List<Map<String, ?>> rows, String section, String... headings) {
            this.rows = rows;
            this.section = section;
            this.headings = headings;
        }

        void add(String c1, String c2, String c3, String c4, String c5) {
            Map<String, Object> m = withHeadings("table");
            m.put("c1", dash(c1));
            m.put("c2", dash(c2));
            m.put("c3", dash(c3));
            m.put("c4", dash(c4));
            m.put("c5", headings[4].isEmpty() ? null : dash(c5));
            rows.add(m);
            count++;
        }

        void close() {
            if (count == 0) rows.add(withHeadings("empty"));
        }

        private Map<String, Object> withHeadings(String kind) {
            Map<String, Object> m = row(section, kind);
            for (int i = 0; i < 5; i++) m.put("h" + (i + 1), headings[i]);
            return m;
        }
    }

    // ---------- formatting ----------

    private static String dash(String s) {
        return s == null || s.isBlank() ? "—" : s;
    }

    private static String date(LocalDate d) {
        return d == null ? null : DATE.format(d);
    }

    private static String dateWithAge(LocalDate dob) {
        if (dob == null) return null;
        return DATE.format(dob) + " (" + Period.between(dob, LocalDate.now()).getYears() + " yrs)";
    }

    private static String serviceLength(LocalDate joining) {
        if (joining == null) return null;
        Period p = Period.between(joining, LocalDate.now());
        return p.getYears() + " yr " + p.getMonths() + " mo";
    }

    private static String amount(BigDecimal amount) {
        return amount == null ? null
                : "Rs " + new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.ENGLISH)).format(amount);
    }

    private static String size(Long bytes) {
        if (bytes == null) return null;
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        return "%.1f MB".formatted(bytes / (1024.0 * 1024.0));
    }

    /** {@code FINAL_WARNING} / {@code "MARRIED"} → "Final Warning" / "Married". */
    private static String pretty(Object value) {
        if (value == null) return null;
        String raw = value instanceof Enum<?> en ? en.name() : value.toString();
        if (raw.isBlank()) return null;
        StringBuilder sb = new StringBuilder(raw.length());
        for (String w : raw.toLowerCase().split("_")) {
            if (w.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    private static String deptName(Map<Long, Department> by, Long id) {
        Department d = id == null ? null : by.get(id);
        return d == null ? null : d.getName();
    }

    private static String desigName(Map<Long, Designation> by, Long id) {
        Designation d = id == null ? null : by.get(id);
        return d == null ? null : d.getTitle();
    }

    private static <T> Map<Long, T> byId(List<T> rows, Function<T, Long> idFn) {
        Map<Long, T> m = new HashMap<>(rows.size());
        for (T t : rows) m.put(idFn.apply(t), t);
        return m;
    }
}
