package com.forward.hrms.employee.service;

import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.ForbiddenError;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.core.error.UnprocessableError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.reports.JasperRenderer;
import com.forward.hrms.employee.client.AttendanceClient;
import com.forward.hrms.employee.client.PayrollClient;
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
import com.forward.hrms.employee.enums.EmployeeErrorCode;
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
import com.forward.hrms.employee.repository.QualificationRepository;
import com.forward.hrms.employee.repository.SectionRepository;
import com.forward.hrms.payroll.dto.response.EmployeeSalaryComponentDto;
import com.forward.hrms.payroll.dto.response.EmployeeSalaryDto;
import com.forward.hrms.payroll.enums.CalcType;
import com.forward.hrms.payroll.enums.ComponentType;

import lombok.RequiredArgsConstructor;

import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Appointment letter PDF in Forward Sports' form layout
 * ({@code jasper/appointment-letter.jrxml}): letterhead, title bar, job type
 * and photo, the appointment paragraph, side-by-side Personal and Official
 * Information panels, and the signature row.
 *
 * <p>Flow: load the employee and its org context → resolve each value
 * (request override → employee record → {@code letter_settings} default) →
 * validate, reporting every missing field in one 422 → fetch salary from
 * payroll when requested → fill the {@code letter_clauses} paragraph →
 * render → audit.
 *
 * <p>Paragraph placeholders (unknown tokens print verbatim so a template typo
 * shows in the preview): {@code {honorific} {employeeName} {relation}
 * {fatherName} {posting} {designation} {department} {heShe} {hisHer}
 * {probationMonths} {probationMonthsWords} {joiningDate} {companyName}}.
 * Values are printed bold.
 *
 * <p>No transaction around the render: the payroll/attendance HTTP calls and
 * the Jasper fill stay off the Hikari pool; only the audit write runs in a
 * short one.
 */
@Service
@RequiredArgsConstructor
public class AppointmentLetterService {

    static final String PAYROLL_READ = "payroll.read";
    static final int MAX_SIGNATORIES = 3;
    /** Allowances the form always prints (0 when not assigned), in this order, ahead of any other earnings. */
    static final List<String[]> FIXED_ALLOWANCES = List.of(
            new String[] {"SPECIAL", "Special Allowance"},
            new String[] {"ACCOMMODATION", "Accommodation"});

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter LETTER_DATE = DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.ENGLISH);
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z]+)}");
    private static final String[] NUMBER_WORDS = {"zero", "one", "two", "three", "four", "five", "six", "seven",
            "eight", "nine", "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen",
            "eighteen", "nineteen", "twenty", "twenty-one", "twenty-two", "twenty-three", "twenty-four"};

    private final EmployeeRepository employees;
    private final DepartmentRepository departments;
    private final SectionRepository sections;
    private final DesignationRepository designations;
    private final GradeRepository grades;
    private final CityRepository cities;
    private final QualificationRepository qualificationCatalog;
    private final EmployeeQualificationRepository qualifications;
    private final EmploymentContractRepository contracts;
    private final EmployeeRequisitionDetailRepository requisitionLines;
    private final LetterClauseRepository clauses;
    private final LetterSettingsService letterSettings;
    private final PayrollClient payroll;
    private final AttendanceClient attendance;
    private final JasperRenderer jasper;
    private final AuditEmitter audit;
    private final TransactionTemplate tx;

    /** The rendered letter plus what the controller needs for the response headers. */
    public record Rendered(byte[] pdf, String filename, String refNo) {}

    /**
     * @param restrictToManagerId non-null when the caller is a manager; only
     *        a direct report's letter may be printed (404 otherwise, so ids
     *        can't be probed)
     */
    public Rendered render(long employeeId, AppointmentLetterRequest request, Long restrictToManagerId) {
        AppointmentLetterRequest req = request == null ? AppointmentLetterRequest.defaults() : request;
        Employee e = employees.findById(employeeId)
                .filter(emp -> restrictToManagerId == null || restrictToManagerId.equals(emp.getManagerId()))
                .orElseThrow(() -> new NotFoundError(EmployeeErrorCode.EMPLOYEE_NOT_FOUND, "employee not found"));
        LetterSettings settings = letterSettings.current();
        Context ctx = context(e, req, settings);

        List<String> missing = missingFields(e, ctx.designation() != null, ctx.department() != null, ctx.joiningDate());
        Salary salary = null;
        if (req.salaryIncluded()) {
            requirePayrollRead();
            salary = salary(e.getId());
            if (salary == null) missing.add("basicSalary");
        }
        if (!missing.isEmpty()) {
            throw new UnprocessableError(EmployeeErrorCode.APPOINTMENT_LETTER_DATA_MISSING,
                    "Cannot generate the appointment letter; missing: " + String.join(", ", missing),
                    Map.of("missingFields", List.copyOf(missing)));
        }

        String refNo = LetterSettingsService.blank(req.refNo())
                ? "%s/APPT/%d/%s".formatted(settings.getRefPrefix(), ctx.letterDate().getYear(), e.getEmployeeCode())
                : req.refNo().trim();
        List<LetterClause> templates = clauses.findAllByLetterTypeAndActiveTrueOrderBySectionAscSeqAsc(LetterType.APPOINTMENT);

        Map<String, Object> params = letterSettings.letterheadParams(settings, null, null);
        params.put("reportTitle", "Appointment Letter");
        params.put("refNo", refNo);
        params.put("letterDate", LETTER_DATE.format(ctx.letterDate()));
        params.put("employeeCode", e.getEmployeeCode());
        params.put("jobType", e.getEmploymentType() == null ? null : pretty(e.getEmploymentType().name()));
        params.put("photo", letterSettings.employeePhoto(e.getProfilePictureKey()));
        params.put("paragraph", paragraph(templates, placeholders(e, ctx, settings)));
        signatories(templates, params);

        byte[] pdf = jasper.renderPdf("/jasper/appointment-letter.jrxml", params,
                new JRMapCollectionDataSource(panelRows(personal(e, ctx), official(e, ctx, salary, req, settings))));

        boolean salaryPrinted = salary != null;
        tx.executeWithoutResult(status -> audit.emit("report", String.valueOf(employeeId), "appointment-letter.run",
                null, Map.of("employeeId", employeeId, "refNo", refNo, "includeSalary", salaryPrinted), true));
        return new Rendered(pdf, "appointment-letter-%s.pdf".formatted(e.getEmployeeCode()), refNo);
    }

    // ---------- resolution ----------

    /** Everything the letter needs besides the employee row itself, with overrides applied. */
    private record Context(LocalDate letterDate, LocalDate joiningDate, int probationMonths,
                           Department department, Section section, Designation designation, Grade grade) {}

    private Context context(Employee e, AppointmentLetterRequest req, LetterSettings settings) {
        Optional<EmploymentContract> probation = contracts.findAllByEmployeeIdOrderByStartDateDesc(e.getId()).stream()
                .filter(c -> c.getStatus() == ContractStatus.ACTIVE && c.getContractType() == ContractType.PROBATION)
                .findFirst();
        int probationMonths = req.probationMonths() != null ? req.probationMonths()
                : probation.map(c -> months(c.getProbationDays())).orElse(settings.getDefaultProbationMonths());
        return new Context(
                req.letterDate() != null ? req.letterDate() : LocalDate.now(),
                req.reportingDate() != null ? req.reportingDate() : e.getJoiningDate(),
                probationMonths,
                e.getDepartmentId() == null ? null : departments.findById(e.getDepartmentId()).orElse(null),
                e.getSectionId() == null ? null : sections.findById(e.getSectionId()).orElse(null),
                e.getDesignationId() == null ? null : designations.findById(e.getDesignationId()).orElse(null),
                e.getGradeId() == null ? null : grades.findById(e.getGradeId()).orElse(null));
    }

    /** Field names match the employee API so the FE can point at the right input. */
    static List<String> missingFields(Employee e, boolean hasDesignation, boolean hasDepartment, LocalDate joiningDate) {
        List<String> missing = new ArrayList<>();
        if (LetterSettingsService.blank(e.getFullName())) missing.add("fullName");
        if (!hasDesignation) missing.add("designation");
        if (!hasDepartment) missing.add("department");
        if (joiningDate == null) missing.add("joiningDate");
        return missing;
    }

    private static void requirePayrollRead() {
        CurrentUser actor = CurrentUserHolder.get().orElse(null);
        if (actor == null || !actor.hasPermission(PAYROLL_READ)) {
            throw new ForbiddenError("Printing salary on the appointment letter requires the payroll.read permission. "
                    + "Generate it with includeSalary=false instead.");
        }
    }

    // ---------- salary ----------

    /** One printed salary line; {@code perDay} lines note it in the label. */
    record SalaryLine(String code, String label, BigDecimal amount, boolean perDay) {}

    record Salary(BigDecimal basic, List<SalaryLine> allowances) {}

    /** Basic + current earning lines from payroll; null when no positive basic is on file (never print a zero salary). */
    private Salary salary(long employeeId) {
        EmployeeSalaryDto current = payroll.currentSalary(employeeId).orElse(null);
        if (current == null || current.basic() == null || current.basic().signum() <= 0) return null;
        List<SalaryLine> allowances = payroll.currentComponents(employeeId).stream()
                .filter(c -> c.type() == ComponentType.EARNING)
                .filter(c -> c.amount() != null && c.amount().signum() > 0)
                .sorted(Comparator.comparing(EmployeeSalaryComponentDto::componentName,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .map(c -> new SalaryLine(upper(c.componentCode()), c.componentName(), c.amount(),
                        c.calcType() == CalcType.PER_DAY))
                .toList();
        return new Salary(current.basic(), allowances);
    }

    // ---------- panels ----------

    private List<String[]> personal(Employee e, Context ctx) {
        EmployeeQualification highest = qualifications.findAllByEmployeeIdOrderByYearOfCompletionDescIdAsc(e.getId())
                .stream().findFirst().orElse(null);
        Qualification q = highest == null || highest.getQualificationId() == null ? null
                : qualificationCatalog.findById(highest.getQualificationId()).orElse(null);
        List<String[]> rows = new ArrayList<>();
        rows.add(kv("Card No", e.getEmployeeCode()));
        rows.add(kv("Name", e.getFullName()));
        rows.add(kv("Father Name", e.getFatherName()));
        rows.add(kv("Husband Name", isGender(e, "FEMALE", "F") ? e.getSpouseName() : null));
        rows.add(kv("CNIC", e.getCnic()));
        rows.add(kv("Date Of Birth", date(e.getDateOfBirth())));
        rows.add(kv("Gender", pretty(e.getGender())));
        rows.add(kv("Religion", e.getReligion()));
        rows.add(kv("Marital Status", pretty(e.getMaritalStatus())));
        rows.add(kv("Qualification", q == null ? null : q.getLevel()));
        rows.add(kv("Specialization", q == null ? null : q.getName()));
        rows.add(kv("Contact No", e.getPhone()));
        rows.add(kv("City", e.getCityId() == null ? null : cities.findById(e.getCityId()).map(City::getName).orElse(null)));
        rows.add(kv("Permanent Address", e.getPermanentAddress()));
        rows.add(kv("Current Address", e.getCurrentAddress()));
        return rows;
    }

    private List<String[]> official(Employee e, Context ctx, Salary salary, AppointmentLetterRequest req,
                                    LetterSettings settings) {
        Long headId = ctx.department().getHeadId();
        EmployeeRequisitionDetail requisition = LetterSettingsService.blank(e.getEmployeeCode()) ? null
                : requisitionLines.findFirstByNewEmployeeCodeOrderByIdDesc(e.getEmployeeCode()).orElse(null);
        List<String[]> rows = new ArrayList<>();
        rows.add(kv("Date Of Joining", date(ctx.joiningDate())));
        rows.add(kv("Date Of Permanent", date(e.getConfirmationDate())));
        rows.add(kv("Department", ctx.department().getName()));
        rows.add(kv("Head Of Department", headId == null ? null : employeeName(headId)));
        rows.add(kv("Section", ctx.section() == null ? null : ctx.section().getName()));
        rows.add(kv("Designation", ctx.designation().getTitle()));
        if (salary != null) salaryRows(salary, rows);
        rows.add(kv("Grade", ctx.grade() == null ? null : ctx.grade().getCode()));
        rows.add(kv("Employment", ctx.designation().getCategory() == null ? null
                : pretty(ctx.designation().getCategory().name())));
        rows.add(kv("Shift", attendance.currentShiftName(e.getId()).orElse(null)));
        rows.add(kv("Branch", LetterSettingsService.blank(req.branch()) ? settings.getDefaultBranch() : req.branch()));
        rows.add(kv("Reference", e.getReferenceBy()));
        rows.add(kv("Request ID", requisition == null || requisition.getRequisitionHeadId() == null ? null
                : String.valueOf(requisition.getRequisitionHeadId())));
        rows.add(kv("Request Against Card No", requisition == null || requisition.getReplacingEmployeeId() == null
                ? null : employees.findById(requisition.getReplacingEmployeeId()).map(Employee::getEmployeeCode).orElse(null)));
        rows.add(kv("Any Other Info", req.otherInfo()));
        return rows;
    }

    /** Basic, then the fixed form allowances (0 when unassigned), then any other earning lines. */
    static void salaryRows(Salary salary, List<String[]> rows) {
        rows.add(kv("Basic Salary", amount(salary.basic())));
        List<SalaryLine> rest = new ArrayList<>(salary.allowances());
        for (String[] fixed : FIXED_ALLOWANCES) {
            SalaryLine line = rest.stream().filter(l -> fixed[0].equals(l.code())).findFirst().orElse(null);
            rest.remove(line);
            rows.add(kv(fixed[1], line == null ? "0" : salaryValue(line)));
        }
        for (SalaryLine l : rest) rows.add(kv(l.label(), salaryValue(l)));
    }

    private static String salaryValue(SalaryLine l) {
        return amount(l.amount()) + (l.perDay() ? " / day" : "");
    }

    /**
     * Zips the two panels into one row list: {@code pl/pv} (personal label/value),
     * {@code ol/ov} (official label/value). {@code first}/{@code last} let the
     * template close the panel borders. The shorter panel pads with blanks.
     */
    static List<Map<String, ?>> panelRows(List<String[]> personal, List<String[]> official) {
        int n = Math.max(personal.size(), official.size());
        List<Map<String, ?>> rows = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String[] p = i < personal.size() ? personal.get(i) : new String[] {null, null};
            String[] o = i < official.size() ? official.get(i) : new String[] {null, null};
            Map<String, Object> m = new HashMap<>();
            m.put("pl", p[0] == null ? null : p[0] + ":");
            m.put("pv", p[1]);
            m.put("ol", o[0] == null ? null : o[0] + ":");
            m.put("ov", o[1]);
            m.put("first", i == 0);
            m.put("last", i == n - 1);
            rows.add(m);
        }
        return rows;
    }

    // ---------- paragraph + signatories ----------

    private Map<String, String> placeholders(Employee e, Context ctx, LetterSettings s) {
        boolean male = isGender(e, "MALE", "M");
        boolean female = isGender(e, "FEMALE", "F");
        Map<String, String> v = new HashMap<>();
        v.put("honorific", male ? "Mr." : female ? "Ms." : "Mr./Ms.");
        v.put("employeeName", bold(e.getFullName()));
        v.put("relation", male ? "S/O" : female ? "D/O" : "S/D/O");
        v.put("fatherName", bold(LetterSettingsService.blank(e.getFatherName()) ? "—" : e.getFatherName().trim()));
        v.put("posting", ctx.section() != null ? "Section " + bold(ctx.section().getName())
                : "the " + bold(ctx.department().getName()) + " department");
        v.put("designation", bold(ctx.designation().getTitle()));
        v.put("department", bold(ctx.department().getName()));
        v.put("heShe", male ? "He" : female ? "She" : "He/She");
        v.put("hisHer", male ? "his" : female ? "her" : "his/her");
        v.put("probationMonths", String.valueOf(ctx.probationMonths()));
        v.put("probationMonthsWords", ctx.probationMonths() < NUMBER_WORDS.length
                ? NUMBER_WORDS[ctx.probationMonths()] : String.valueOf(ctx.probationMonths()));
        v.put("joiningDate", bold(date(ctx.joiningDate())));
        v.put("companyName", escape(s.getCompanyLegalName()));
        return v;
    }

    /** OPENING clauses joined; the template renders it with markup="styled", so text is XML-escaped. */
    private static String paragraph(List<LetterClause> templates, Map<String, String> vars) {
        return templates.stream()
                .filter(c -> c.getSection() == LetterClauseSection.OPENING)
                .sorted(Comparator.comparingInt(LetterClause::getSeq))
                .map(c -> fill(escape(c.getBody()), vars))
                .reduce((a, b) -> a + "\n\n" + b)
                .orElse("");
    }

    private static void signatories(List<LetterClause> templates, Map<String, Object> params) {
        List<LetterClause> sigs = templates.stream()
                .filter(c -> c.getSection() == LetterClauseSection.SIGNATORY)
                .sorted(Comparator.comparingInt(LetterClause::getSeq))
                .limit(MAX_SIGNATORIES)
                .toList();
        for (int i = 0; i < sigs.size(); i++) {
            params.put("sig" + (i + 1) + "Title", sigs.get(i).getTitle());
            params.put("sig" + (i + 1) + "Sub", LetterSettingsService.blank(sigs.get(i).getBody()) ? null : sigs.get(i).getBody());
        }
    }

    static String fill(String template, Map<String, String> vars) {
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String value = vars.get(m.group(1));
            m.appendReplacement(out, Matcher.quoteReplacement(value == null ? m.group() : value));
        }
        return m.appendTail(out).toString();
    }

    // ---------- formatting ----------

    /** Bold span for Jasper's styled markup. Helvetica-Bold is named explicitly: no font extension is registered. */
    private static String bold(String s) {
        return "<style isBold=\"true\" pdfFontName=\"Helvetica-Bold\">" + escape(s) + "</style>";
    }

    static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    static String amount(BigDecimal amount) {
        return new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.ENGLISH)).format(amount);
    }

    private static String[] kv(String label, String value) {
        return new String[] {label, LetterSettingsService.blank(value) ? null : value.trim()};
    }

    private String employeeName(long id) {
        return employees.findById(id).map(Employee::getFullName).orElse(null);
    }

    private static String date(LocalDate d) {
        return d == null ? null : DATE.format(d);
    }

    private static int months(Integer probationDays) {
        if (probationDays == null || probationDays <= 0) return 0;
        return Math.max(1, (int) Math.round(probationDays / 30.0));
    }

    private static boolean isGender(Employee e, String... values) {
        String g = upper(e.getGender());
        for (String v : values) if (v.equals(g)) return true;
        return false;
    }

    private static String upper(String s) {
        return s == null ? "" : s.trim().toUpperCase(Locale.ROOT);
    }

    private static String pretty(String raw) {
        if (LetterSettingsService.blank(raw)) return null;
        String s = raw.trim().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
