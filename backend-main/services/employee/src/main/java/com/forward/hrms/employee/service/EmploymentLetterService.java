package com.forward.hrms.employee.service;

import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.core.error.UnprocessableError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.reports.JasperRenderer;
import com.forward.hrms.employee.dto.request.EmployeeReportFilter;
import com.forward.hrms.employee.entity.Department;
import com.forward.hrms.employee.entity.DisciplinaryAction;
import com.forward.hrms.employee.entity.Designation;
import com.forward.hrms.employee.entity.Employee;
import com.forward.hrms.employee.entity.EmployeeExit;
import com.forward.hrms.employee.entity.LetterSettings;
import com.forward.hrms.employee.enums.DisciplinaryDecision;
import com.forward.hrms.employee.enums.EmployeeErrorCode;
import com.forward.hrms.employee.enums.EmploymentStatus;
import com.forward.hrms.employee.enums.ExitType;
import com.forward.hrms.employee.repository.DepartmentRepository;
import com.forward.hrms.employee.repository.DesignationRepository;
import com.forward.hrms.employee.repository.DisciplinaryActionRepository;
import com.forward.hrms.employee.repository.EmployeeExitRepository;
import com.forward.hrms.employee.repository.EmployeeRepository;

import lombok.RequiredArgsConstructor;

import net.sf.jasperreports.engine.JREmptyDataSource;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Confirmation, relieving and character letters on the shared letter template
 * ({@code jasper/employee-letter.jrxml}, letterhead and signatory from
 * {@code letter_settings}).
 * <ul>
 *   <li><b>Confirmation</b> — services confirmed from the employee's
 *       {@code confirmation_date}. One employee, or everyone whose
 *       confirmation date falls in a month (legacy "Job Confirmation /
 *       Permanent Letter, By Month"), merged into one PDF.</li>
 *   <li><b>Relieving</b> — for a left employee: relieved from service on the
 *       last working day of their most recent exit (legacy "Left Employee
 *       Letter").</li>
 *   <li><b>Character certificate</b> — good conduct during service, for a
 *       current or left employee. Refused while any disciplinary action is
 *       upheld or still undecided: the company must not certify good
 *       character against its own record. Withdrawn actions don't count.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class EmploymentLetterService {

    static final int MAX_LETTERS = 300;
    private static final DateTimeFormatter LETTER_DATE = DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);

    private final EmployeeRepository employees;
    private final EmployeeExitRepository exits;
    private final DepartmentRepository departments;
    private final DesignationRepository designations;
    private final DisciplinaryActionRepository disciplinary;
    private final EmployeeReportScope scope;
    private final LetterSettingsService letterSettings;
    private final JasperRenderer jasper;
    private final AuditEmitter audit;
    private final TransactionTemplate tx;

    // ---------- confirmation ----------

    public byte[] confirmationLetter(long employeeId, Long restrictToManagerId) {
        Employee e = find(employeeId, restrictToManagerId);
        if (e.getConfirmationDate() == null) {
            throw new UnprocessableError(EmployeeErrorCode.VALIDATION_FAILED,
                    "No confirmation date on file for " + e.getEmployeeCode() + "; set it on the employee first.",
                    Map.of("missingFields", List.of("confirmationDate")));
        }
        byte[] pdf = render(List.of(e), this::confirmationBody, "Confirmation Letter");
        auditRun("confirmation-letter.run", String.valueOf(employeeId), Map.of("employeeId", employeeId));
        return pdf;
    }

    /** Everyone matching the filter whose confirmation date falls in {@code month}, one letter per page. */
    public byte[] confirmationLetters(YearMonth month, EmployeeReportFilter filter, Long restrictToManagerId) {
        if (month == null) throw new UnprocessableError(EmployeeErrorCode.VALIDATION_FAILED, "month is required (yyyy-MM).");
        List<Employee> people = scope.select(filter, restrictToManagerId).stream()
                .filter(e -> e.getConfirmationDate() != null && YearMonth.from(e.getConfirmationDate()).equals(month))
                .toList();
        if (people.isEmpty()) {
            throw new UnprocessableError(EmployeeErrorCode.VALIDATION_FAILED,
                    "No employees are confirmed in " + MONTH.format(month) + " for this filter.");
        }
        tooMany(people.size());
        byte[] pdf = render(people, this::confirmationBody, "Confirmation Letter");
        auditRun("confirmation-letters.run", "0", Map.of("month", month.toString(), "count", people.size()));
        return pdf;
    }

    private String confirmationBody(Employee e, Names n, String company) {
        return "Dear " + honorific(e) + " " + e.getFullName() + ",\n\n"
                + "We are pleased to inform you that, on successful completion of your probation period, your "
                + "services as " + n.designation() + " in the " + n.department() + " department are hereby "
                + "confirmed with effect from " + LETTER_DATE.format(e.getConfirmationDate()) + ".\n\n"
                + "All other terms and conditions of your appointment remain unchanged. We appreciate your "
                + "contribution and look forward to your continued commitment to " + company + ".\n\n"
                + "Please sign the duplicate copy of this letter as a token of your acceptance.";
    }

    // ---------- relieving ----------

    public byte[] relievingLetter(long employeeId, Long restrictToManagerId) {
        Employee e = find(employeeId, restrictToManagerId);
        if (e.getEmploymentStatus() != EmploymentStatus.TERMINATED) {
            throw new UnprocessableError(EmployeeErrorCode.VALIDATION_FAILED,
                    "Relieving letter is for employees who have left; " + e.getEmployeeCode() + " is still in service.");
        }
        EmployeeExit exit = exits.findFirstByEmployeeIdOrderByIdDesc(employeeId).orElse(null);
        if (exit == null || exit.getLastWorkingDay() == null) {
            throw new UnprocessableError(EmployeeErrorCode.VALIDATION_FAILED,
                    "No exit with a last working day on file for " + e.getEmployeeCode() + ".",
                    Map.of("missingFields", List.of("lastWorkingDay")));
        }
        byte[] pdf = render(List.of(e), (emp, n, company) -> relievingBody(emp, n, company, exit), "Relieving Letter");
        auditRun("relieving-letter.run", String.valueOf(employeeId), Map.of("employeeId", employeeId));
        return pdf;
    }

    private static String relievingBody(Employee e, Names n, String company, EmployeeExit exit) {
        String reason = exit.getExitType() == ExitType.RESIGNATION ? "upon acceptance of " + hisHer(e) + " resignation"
                : exit.getExitType() == ExitType.RETIREMENT ? "upon retirement"
                : "upon termination of " + hisHer(e) + " services";
        StringBuilder sb = new StringBuilder("To Whom It May Concern,\n\n");
        sb.append("This is to certify that ").append(honorific(e)).append(' ').append(e.getFullName());
        if (e.getFatherName() != null && !e.getFatherName().isBlank()) {
            sb.append(' ').append(EmployeeCardService.relationLabel(e.getGender())).append(' ').append(e.getFatherName().trim());
        }
        sb.append(", Card No. ").append(e.getEmployeeCode());
        if (e.getCnic() != null && !e.getCnic().isBlank()) sb.append(", CNIC ").append(e.getCnic().trim());
        sb.append(", worked with ").append(company).append(" as ").append(n.designation()).append(" in the ")
                .append(n.department()).append(" department");
        if (e.getJoiningDate() != null) sb.append(" from ").append(LETTER_DATE.format(e.getJoiningDate()));
        sb.append(" to ").append(LETTER_DATE.format(exit.getLastWorkingDay())).append(".\n\n");
        sb.append(heShe(e)).append(" has been relieved from the services of the company with effect from the close of ")
                .append("business on ").append(LETTER_DATE.format(exit.getLastWorkingDay())).append(", ").append(reason)
                .append(".\n\nWe wish ").append(himHer(e)).append(" success in future endeavours.");
        return sb.toString();
    }

    // ---------- character ----------

    public byte[] characterCertificate(long employeeId, Long restrictToManagerId) {
        Employee e = find(employeeId, restrictToManagerId);
        List<DisciplinaryAction> open = disciplinary.listForEmployee(employeeId, null, null, null, null).stream()
                .filter(d -> d.getDecision() != DisciplinaryDecision.WITHDRAWN)
                .toList();
        if (!open.isEmpty()) {
            throw new UnprocessableError(EmployeeErrorCode.VALIDATION_FAILED,
                    "%s has %d disciplinary action(s) on record that are upheld or undecided; a character certificate can't be issued."
                            .formatted(e.getEmployeeCode(), open.size()),
                    Map.of("disciplinaryActions", open.stream().map(DisciplinaryAction::getId).toList()));
        }
        EmployeeExit exit = e.getEmploymentStatus() == EmploymentStatus.TERMINATED
                ? exits.findFirstByEmployeeIdOrderByIdDesc(employeeId).orElse(null) : null;
        byte[] pdf = render(List.of(e), (emp, n, company) -> characterBody(emp, n, company, exit), "Character Certificate");
        auditRun("character-certificate.run", String.valueOf(employeeId), Map.of("employeeId", employeeId));
        return pdf;
    }

    private static String characterBody(Employee e, Names n, String company, EmployeeExit exit) {
        boolean left = exit != null && exit.getLastWorkingDay() != null;
        StringBuilder sb = new StringBuilder("To Whom It May Concern,\n\n");
        sb.append("This is to certify that ").append(honorific(e)).append(' ').append(e.getFullName());
        if (e.getFatherName() != null && !e.getFatherName().isBlank()) {
            sb.append(' ').append(EmployeeCardService.relationLabel(e.getGender())).append(' ').append(e.getFatherName().trim());
        }
        sb.append(", Card No. ").append(e.getEmployeeCode());
        if (e.getCnic() != null && !e.getCnic().isBlank()) sb.append(", CNIC ").append(e.getCnic().trim());
        sb.append(left ? ", worked with " : ", is working with ").append(company).append(" as ")
                .append(n.designation()).append(" in the ").append(n.department()).append(" department");
        if (e.getJoiningDate() != null) {
            sb.append(left ? " from " : " since ").append(LETTER_DATE.format(e.getJoiningDate()));
        }
        if (left) sb.append(" to ").append(LETTER_DATE.format(exit.getLastWorkingDay()));
        sb.append(".\n\nDuring ").append(hisHer(e)).append(" service with us, ").append(hisHer(e))
                .append(" conduct and character remained good. To the best of our knowledge, ").append(heShe(e).toLowerCase(Locale.ROOT))
                .append(" bears a good moral character and has not been involved in any activity prejudicial to ")
                .append("the interest of the company.\n\nThis certificate is issued at ").append(hisHer(e))
                .append(" request.");
        return sb.toString();
    }

    // ---------- rendering ----------

    private record Names(String designation, String department) {}

    @FunctionalInterface
    private interface Body {
        String of(Employee e, Names names, String company);
    }

    private byte[] render(List<Employee> people, Body body, String title) {
        Map<Long, Department> deptById = byId(departments.findAll(), Department::getId);
        Map<Long, Designation> desigById = byId(designations.findAll(), Designation::getId);
        LetterSettings settings = letterSettings.current();
        List<byte[]> pdfs = new ArrayList<>(people.size());
        for (Employee e : people) {
            Designation g = e.getDesignationId() == null ? null : desigById.get(e.getDesignationId());
            Department d = e.getDepartmentId() == null ? null : deptById.get(e.getDepartmentId());
            Names names = new Names(g == null ? "—" : g.getTitle(), d == null ? "—" : d.getName());
            Map<String, Object> p = letterSettings.letterheadParams(settings, null, null);
            p.put("letterTitle", title);
            p.put("letterDate", LETTER_DATE.format(LocalDate.now()));
            p.put("employeeName", e.getFullName());
            p.put("employeeCode", e.getEmployeeCode());
            p.put("designation", names.designation());
            p.put("department", names.department());
            p.put("body", body.of(e, names, settings.getCompanyLegalName()));
            pdfs.add(jasper.renderPdf("/jasper/employee-letter.jrxml", p, new JREmptyDataSource(1)));
        }
        return PdfMerge.merge(pdfs);
    }

    private Employee find(long employeeId, Long restrictToManagerId) {
        return employees.findById(employeeId)
                .filter(emp -> restrictToManagerId == null || restrictToManagerId.equals(emp.getManagerId()))
                .orElseThrow(() -> new NotFoundError(EmployeeErrorCode.EMPLOYEE_NOT_FOUND, "employee not found"));
    }

    private static void tooMany(int n) {
        if (n > MAX_LETTERS) {
            throw new UnprocessableError(EmployeeErrorCode.VALIDATION_FAILED,
                    "%d letters match; print at most %d at a time — narrow the filter.".formatted(n, MAX_LETTERS));
        }
    }

    private void auditRun(String action, String entityId, Map<String, Object> after) {
        tx.executeWithoutResult(status -> audit.emit("report", entityId, action, null, after, true));
    }

    private static boolean female(Employee e) {
        String g = e.getGender() == null ? "" : e.getGender().trim().toUpperCase(Locale.ROOT);
        return g.equals("FEMALE") || g.equals("F");
    }

    private static boolean male(Employee e) {
        String g = e.getGender() == null ? "" : e.getGender().trim().toUpperCase(Locale.ROOT);
        return g.equals("MALE") || g.equals("M");
    }

    private static String honorific(Employee e) {
        return male(e) ? "Mr." : female(e) ? "Ms." : "Mr./Ms.";
    }

    private static String heShe(Employee e) {
        return male(e) ? "He" : female(e) ? "She" : "He/She";
    }

    private static String hisHer(Employee e) {
        return male(e) ? "his" : female(e) ? "her" : "his/her";
    }

    private static String himHer(Employee e) {
        return male(e) ? "him" : female(e) ? "her" : "him/her";
    }

    private static <T> Map<Long, T> byId(List<T> rows, Function<T, Long> idFn) {
        Map<Long, T> m = new HashMap<>(rows.size());
        for (T t : rows) m.put(idFn.apply(t), t);
        return m;
    }
}
