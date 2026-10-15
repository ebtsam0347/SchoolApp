package com.forward.hrms.employee.service;

import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.UnprocessableError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.reports.XlsxReport;
import com.forward.hrms.employee.dto.request.EmployeeReportFilter;
import com.forward.hrms.employee.entity.Department;
import com.forward.hrms.employee.entity.Designation;
import com.forward.hrms.employee.entity.Disability;
import com.forward.hrms.employee.entity.Employee;
import com.forward.hrms.employee.entity.EmployeeDisability;
import com.forward.hrms.employee.entity.EmployeeExit;
import com.forward.hrms.employee.entity.EmployeeHistory;
import com.forward.hrms.employee.entity.Section;
import com.forward.hrms.employee.enums.EmployeeErrorCode;
import com.forward.hrms.employee.enums.HistoryChangeReason;
import com.forward.hrms.employee.repository.DepartmentRepository;
import com.forward.hrms.employee.repository.DesignationRepository;
import com.forward.hrms.employee.repository.DisabilityRepository;
import com.forward.hrms.employee.repository.EmployeeDisabilityRepository;
import com.forward.hrms.employee.repository.EmployeeExitRepository;
import com.forward.hrms.employee.repository.EmployeeHistoryRepository;
import com.forward.hrms.employee.repository.SectionRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Two HR registers (Excel), both taking the common {@link EmployeeReportFilter}:
 * <ul>
 *   <li><b>Disability list</b> — every recorded disability of matching
 *       employees with severity and whether a certificate is on file
 *       (legacy "Disability Info"). Supports the disabled-persons quota
 *       return.</li>
 *   <li><b>Rejoining list</b> — reinstatements ({@code employee_history}
 *       rows with reason REINSTATED) in a period, with the exit they reversed
 *       and the gap in days (legacy "Rejoining List").</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class EmployeeRegistersReportService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm", Locale.ENGLISH);

    private static final String[] DISABILITY_COLS = {"#", "Card No", "Name", "Gender", "Department", "Section",
            "Designation", "Date of Joining", "Disability", "Severity", "Description", "Certificate", "Status"};
    private static final int[] DISABILITY_W = {5, 10, 26, 9, 22, 20, 22, 13, 22, 10, 34, 11, 11};
    private static final String[] REJOIN_COLS = {"#", "Rejoined On", "Card No", "Name", "Department", "Designation",
            "Original Joining", "Left On", "Exit Type", "Gap (days)", "Notes"};
    private static final int[] REJOIN_W = {5, 13, 10, 26, 22, 22, 14, 13, 13, 10, 34};

    private final EmployeeReportScope scope;
    private final EmployeeDisabilityRepository employeeDisabilities;
    private final DisabilityRepository disabilityCatalog;
    private final EmployeeHistoryRepository history;
    private final EmployeeExitRepository exits;
    private final DepartmentRepository departments;
    private final SectionRepository sections;
    private final DesignationRepository designations;
    private final LetterSettingsService letterSettings;
    private final AuditEmitter audit;

    // ---------- disability list ----------

    @Transactional
    public void writeDisabilitiesXlsx(OutputStream out, EmployeeReportFilter filter, Long restrictToManagerId) throws IOException {
        Map<Long, Employee> people = scope.select(filter, restrictToManagerId).stream()
                .collect(Collectors.toMap(Employee::getId, Function.identity(), (a, b) -> a, java.util.LinkedHashMap::new));
        Map<Long, List<EmployeeDisability>> byEmp = employeeDisabilities.findAll().stream()
                .filter(d -> people.containsKey(d.getEmployeeId()))
                .collect(Collectors.groupingBy(EmployeeDisability::getEmployeeId));
        Map<Long, Disability> catalog = byId(disabilityCatalog.findAll(), Disability::getId);
        Names names = names();

        int rows = 0;
        try (XlsxReport wb = new XlsxReport("Disabilities")) {
            wb.header(company(), "Disability List", "Filters: " + scope.describe(filter, restrictToManagerId),
                    generated(), DISABILITY_COLS, DISABILITY_W);
            for (Employee e : people.values()) {
                for (EmployeeDisability d : byEmp.getOrDefault(e.getId(), List.of()).stream()
                        .sorted(Comparator.comparing(EmployeeDisability::getId)).toList()) {
                    Disability c = d.getDisabilityId() == null ? null : catalog.get(d.getDisabilityId());
                    wb.row(++rows, e.getEmployeeCode(), e.getFullName(), EmployeeListReportService.pretty(e.getGender()),
                            names.department(e.getDepartmentId()), names.section(e.getSectionId()),
                            names.designation(e.getDesignationId()), date(e.getJoiningDate()),
                            c == null ? null : c.getName(),
                            d.getSeverity() == null ? null : EmployeeListReportService.pretty(d.getSeverity().name()),
                            d.getDescription(), blank(d.getDocumentKey()) ? "Not on file" : "On file",
                            EmployeeListReportService.pretty(e.getEmploymentStatus() == null ? null : e.getEmploymentStatus().name()));
                }
            }
            wb.totalRow("", "Total", "", rows + " record(s), " + byEmp.size() + " employee(s)");
            wb.write(out);
        }
        audit.emit("report", "0", "disability-list.run", null, Map.of("rowCount", rows, "employees", byEmp.size()), false);
    }

    // ---------- rejoining list ----------

    /**
     * @param from default one year before {@code to}
     * @param to   default today
     */
    @Transactional
    public void writeRejoiningsXlsx(OutputStream out, EmployeeReportFilter filter, LocalDate from, LocalDate to,
                                    Long restrictToManagerId) throws IOException {
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.minusYears(1) : from;
        if (start.isAfter(end)) {
            throw new UnprocessableError(EmployeeErrorCode.VALIDATION_FAILED, "from must be on or before to");
        }
        Map<Long, Employee> people = scope.select(filter, restrictToManagerId).stream()
                .collect(Collectors.toMap(Employee::getId, Function.identity()));
        List<EmployeeHistory> reinstated = history.findAll().stream()
                .filter(h -> h.getChangeReason() == HistoryChangeReason.REINSTATED)
                .filter(h -> h.getEffectiveFrom() != null && !h.getEffectiveFrom().isBefore(start) && !h.getEffectiveFrom().isAfter(end))
                .filter(h -> people.containsKey(h.getEmployeeId()))
                .sorted(Comparator.comparing(EmployeeHistory::getEffectiveFrom).thenComparing(EmployeeHistory::getId))
                .toList();
        Map<Long, List<EmployeeExit>> exitsByEmp = exits.findAll().stream()
                .filter(x -> x.getLastWorkingDay() != null)
                .collect(Collectors.groupingBy(EmployeeExit::getEmployeeId));
        Names names = names();

        try (XlsxReport wb = new XlsxReport("Rejoinings")) {
            wb.header(company(), "Rejoining List",
                    "Period: " + DATE.format(start) + " to " + DATE.format(end) + "   |   Filters: " + scope.describe(filter, restrictToManagerId),
                    generated(), REJOIN_COLS, REJOIN_W);
            int n = 0;
            for (EmployeeHistory h : reinstated) {
                Employee e = people.get(h.getEmployeeId());
                EmployeeExit reversed = exitsByEmp.getOrDefault(e.getId(), List.of()).stream()
                        .filter(x -> !x.getLastWorkingDay().isAfter(h.getEffectiveFrom()))
                        .max(Comparator.comparing(EmployeeExit::getLastWorkingDay).thenComparing(EmployeeExit::getId))
                        .orElse(null);
                wb.row(++n, date(h.getEffectiveFrom()), e.getEmployeeCode(), e.getFullName(),
                        names.department(h.getDepartmentId()), names.designation(h.getDesignationId()),
                        date(e.getJoiningDate()),
                        reversed == null ? null : date(reversed.getLastWorkingDay()),
                        reversed == null || reversed.getExitType() == null ? null
                                : EmployeeListReportService.pretty(reversed.getExitType().name()),
                        reversed == null ? null : ChronoUnit.DAYS.between(reversed.getLastWorkingDay(), h.getEffectiveFrom()),
                        h.getNotes());
            }
            wb.totalRow("", "Total", "", n + " rejoining(s)");
            wb.write(out);
        }
        audit.emit("report", "0", "rejoining-list.run", null,
                Map.of("from", start, "to", end, "rowCount", reinstated.size()), false);
    }

    // ---------- helpers ----------

    private record Names(Map<Long, Department> depts, Map<Long, Section> secs, Map<Long, Designation> desigs) {
        String department(Long id) {
            Department d = id == null ? null : depts.get(id);
            return d == null ? null : d.getName();
        }

        String section(Long id) {
            Section s = id == null ? null : secs.get(id);
            return s == null ? null : s.getName();
        }

        String designation(Long id) {
            Designation d = id == null ? null : desigs.get(id);
            return d == null ? null : d.getTitle();
        }
    }

    private Names names() {
        return new Names(byId(departments.findAll(), Department::getId), byId(sections.findAll(), Section::getId),
                byId(designations.findAll(), Designation::getId));
    }

    private String company() {
        return letterSettings.current().getCompanyLegalName();
    }

    private static String generated() {
        CurrentUser actor = CurrentUserHolder.get().orElse(null);
        return "Generated " + STAMP.format(LocalDateTime.now()) + " by "
                + (actor == null ? CurrentUser.SYSTEM_USERNAME : actor.username());
    }

    private static String date(LocalDate d) {
        return d == null ? null : DATE.format(d);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static <T> Map<Long, T> byId(List<T> rows, Function<T, Long> idFn) {
        Map<Long, T> m = new HashMap<>(rows.size());
        for (T t : rows) m.put(idFn.apply(t), t);
        return m;
    }
}
