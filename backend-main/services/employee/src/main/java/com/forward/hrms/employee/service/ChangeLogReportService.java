package com.forward.hrms.employee.service;

import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.UnprocessableError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.reports.XlsxReport;
import com.forward.hrms.employee.dto.request.EmployeeReportFilter;
import com.forward.hrms.employee.entity.Department;
import com.forward.hrms.employee.entity.Designation;
import com.forward.hrms.employee.entity.Employee;
import com.forward.hrms.employee.entity.EmployeeHistory;
import com.forward.hrms.employee.entity.Grade;
import com.forward.hrms.employee.entity.Section;
import com.forward.hrms.employee.enums.ChangeLogField;
import com.forward.hrms.employee.enums.EmployeeErrorCode;
import com.forward.hrms.employee.repository.DepartmentRepository;
import com.forward.hrms.employee.repository.DesignationRepository;
import com.forward.hrms.employee.repository.EmployeeHistoryRepository;
import com.forward.hrms.employee.repository.GradeRepository;
import com.forward.hrms.employee.repository.SectionRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Change Log (Excel) — department / section / designation / grade changes
 * derived from consecutive {@code employee_history} rows. Replaces the
 * legacy Department, Section, Designation and Grade logs. The onboarding
 * row has nothing to compare against and is skipped. Salary history lives
 * in payroll and shift history in attendance; they get their own logs.
 */
@Service
@RequiredArgsConstructor
public class ChangeLogReportService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm", Locale.ENGLISH);
    private static final String[] COLS = {"#", "Effective Date", "Card No", "Name", "Change", "From", "To",
            "Reason", "Notes", "Recorded By", "Recorded On"};
    private static final int[] WIDTHS = {5, 13, 10, 26, 13, 26, 26, 18, 30, 16, 17};

    private final EmployeeReportScope scope;
    private final EmployeeHistoryRepository history;
    private final DepartmentRepository departments;
    private final SectionRepository sections;
    private final DesignationRepository designations;
    private final GradeRepository grades;
    private final LetterSettingsService letterSettings;
    private final AuditEmitter audit;

    record Change(LocalDate effective, Employee employee, ChangeLogField field, String from, String to,
                  EmployeeHistory row) {}

    /**
     * @param field null → all four fields
     * @param from  default one year before {@code to}
     * @param to    default today
     */
    @Transactional
    public void writeXlsx(OutputStream out, EmployeeReportFilter filter, ChangeLogField field,
                          LocalDate from, LocalDate to, Long restrictToManagerId) throws IOException {
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.minusYears(1) : from;
        if (start.isAfter(end)) {
            throw new UnprocessableError(EmployeeErrorCode.VALIDATION_FAILED, "from must be on or before to");
        }
        List<Employee> people = scope.select(filter, restrictToManagerId);
        Map<Long, List<EmployeeHistory>> byEmp = history.findAll().stream()
                .collect(Collectors.groupingBy(EmployeeHistory::getEmployeeId));
        Map<Long, String> deptName = names(departments.findAll(), Department::getId, Department::getName);
        Map<Long, String> sectionName = names(sections.findAll(), Section::getId, Section::getName);
        Map<Long, String> desigName = names(designations.findAll(), Designation::getId, Designation::getTitle);
        Map<Long, String> gradeName = names(grades.findAll(), Grade::getId, Grade::getCode);

        List<Change> changes = new ArrayList<>();
        for (Employee e : people) {
            List<EmployeeHistory> rows = byEmp.getOrDefault(e.getId(), List.of()).stream()
                    .filter(h -> h.getEffectiveFrom() != null)
                    .sorted(Comparator.comparing(EmployeeHistory::getEffectiveFrom).thenComparing(EmployeeHistory::getId))
                    .toList();
            for (int i = 1; i < rows.size(); i++) {
                EmployeeHistory prev = rows.get(i - 1), cur = rows.get(i);
                if (cur.getEffectiveFrom().isBefore(start) || cur.getEffectiveFrom().isAfter(end)) continue;
                for (ChangeLogField f : ChangeLogField.values()) {
                    if (field != null && field != f) continue;
                    Long a = idOf(prev, f), b = idOf(cur, f);
                    if (Objects.equals(a, b)) continue;
                    Map<Long, String> names = switch (f) {
                        case DEPARTMENT -> deptName;
                        case SECTION -> sectionName;
                        case DESIGNATION -> desigName;
                        case GRADE -> gradeName;
                    };
                    changes.add(new Change(cur.getEffectiveFrom(), e, f, label(names, a), label(names, b), cur));
                }
            }
        }
        changes.sort(Comparator.comparing(Change::effective)
                .thenComparing(c -> c.employee().getEmployeeCode(), Comparator.nullsLast(EmployeeReportFilter::compareCards)));

        String title = "Change Log — " + (field == null ? "Department, Section, Designation & Grade"
                : EmployeeListReportService.pretty(field.name()));
        try (XlsxReport wb = new XlsxReport("Change log")) {
            wb.header(letterSettings.current().getCompanyLegalName(), title,
                    "Period: " + DATE.format(start) + " to " + DATE.format(end)
                            + "   |   Filters: " + scope.describe(filter, restrictToManagerId),
                    generatedLine(changes.size()), COLS, WIDTHS);
            int n = 0;
            for (Change c : changes) {
                EmployeeHistory h = c.row();
                wb.row(++n, DATE.format(c.effective()), c.employee().getEmployeeCode(), c.employee().getFullName(),
                        EmployeeListReportService.pretty(c.field().name()), c.from(), c.to(),
                        h.getChangeReason() == null ? null : EmployeeListReportService.pretty(h.getChangeReason().name()),
                        h.getNotes(), h.getCreatedBy(),
                        h.getCreatedAt() == null ? null : STAMP.format(h.getCreatedAt()));
            }
            wb.totalRow("", "Total", "", changes.size() + " change(s)");
            wb.write(out);
        }
        audit.emit("report", "0", "change-log.run", null,
                Map.of("field", field == null ? "ALL" : field.name(), "from", start, "to", end,
                        "rowCount", changes.size()), false);
    }

    private static Long idOf(EmployeeHistory h, ChangeLogField f) {
        return switch (f) {
            case DEPARTMENT -> h.getDepartmentId();
            case SECTION -> h.getSectionId();
            case DESIGNATION -> h.getDesignationId();
            case GRADE -> h.getGradeId();
        };
    }

    private static String label(Map<Long, String> names, Long id) {
        if (id == null) return "—";
        return names.getOrDefault(id, "#" + id);
    }

    private static String generatedLine(int count) {
        CurrentUser actor = CurrentUserHolder.get().orElse(null);
        return "Generated " + STAMP.format(LocalDateTime.now()) + " by "
                + (actor == null ? CurrentUser.SYSTEM_USERNAME : actor.username()) + "   |   " + count + " change(s)";
    }

    private static <T> Map<Long, String> names(List<T> rows, Function<T, Long> id, Function<T, String> name) {
        Map<Long, String> m = new HashMap<>(rows.size());
        for (T t : rows) m.put(id.apply(t), name.apply(t));
        return m;
    }
}
