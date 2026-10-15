package com.forward.hrms.payroll.service;

import com.forward.hrms.commons.cache.EmployeeDisplay;
import com.forward.hrms.commons.cache.EmployeeDisplayCache;
import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.reports.XlsxReport;
import com.forward.hrms.employee.enums.EmploymentStatus;
import com.forward.hrms.payroll.client.EmployeeClient;
import com.forward.hrms.payroll.entity.EmployeePayrollSettings;
import com.forward.hrms.payroll.repository.EmployeePayrollSettingsRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Joining vs Gratuity Date (Excel) — for every active employee on payroll,
 * the joining date (employee-service) beside the date gratuity is counted
 * from ({@code gratuity_start_date}, else payroll's hire date — the same
 * rule {@link GratuityService} applies). By default lists only employees
 * where the two differ: rejoiners, transfers-in and data-entry slips that
 * would change a final settlement (legacy "Different DOJ and Gratuity Date").
 */
@Service
@RequiredArgsConstructor
public class GratuityDatesReportService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm", Locale.ENGLISH);
    private static final String[] COLS = {"#", "Card No", "Name", "Department", "Designation", "Date of Joining",
            "Payroll Hire Date", "Gratuity Start (set)", "Gratuity Counted From", "Difference (days)", "Gratuity Service"};
    private static final int[] WIDTHS = {5, 10, 26, 22, 22, 14, 14, 16, 16, 12, 13};

    private final EmployeePayrollSettingsRepository settings;
    private final EmployeeClient employees;
    private final EmployeeDisplayCache displays;
    private final AuditEmitter audit;

    record Row(EmployeePayrollSettings s, EmployeeClient.EmployeeBrief who, EmployeeDisplay display, LocalDate countedFrom) {}

    /** What the sheet shows; built on the request thread because the employee-service call needs the caller's identity. */
    public record Loaded(List<Row> rows, boolean onlyDifferent, Long departmentId) {}

    public void writeXlsx(OutputStream out, boolean onlyDifferent, Long departmentId) throws IOException {
        write(out, load(onlyDifferent, departmentId));
    }

    @Transactional
    public Loaded load(boolean onlyDifferent, Long departmentId) {
        List<EmployeePayrollSettings> active = settings.findAllByActiveTrue();
        List<Long> ids = active.stream().map(EmployeePayrollSettings::getEmployeeId).distinct().toList();
        Map<Long, EmployeeClient.EmployeeBrief> briefs = employees.getMany(ids);
        Map<Long, EmployeeDisplay> display = displays.getMany(ids);

        List<Row> rows = active.stream()
                .map(s -> new Row(s, briefs.get(s.getEmployeeId()), display.get(s.getEmployeeId()),
                        s.getGratuityStartDate() != null ? s.getGratuityStartDate() : s.getHireDate()))
                .filter(r -> r.who() != null && r.who().employmentStatus() != null
                        && r.who().employmentStatus() != EmploymentStatus.TERMINATED)
                .filter(r -> departmentId == null || (r.display() != null && departmentId.equals(r.display().departmentId())))
                .filter(r -> !onlyDifferent || !Objects.equals(r.who().joiningDate(), r.countedFrom()))
                .sorted(Comparator.comparing((Row r) -> r.who().employeeCode(), GratuityDatesReportService::compareCards))
                .toList();
        audit.emit("report", "0", "gratuity-dates.run", null,
                Map.of("onlyDifferent", onlyDifferent, "rowCount", rows.size()), false);
        return new Loaded(rows, onlyDifferent, departmentId);
    }

    public void write(OutputStream out, Loaded loaded) throws IOException {
        List<Row> rows = loaded.rows();
        boolean onlyDifferent = loaded.onlyDifferent();
        Long departmentId = loaded.departmentId();
        LocalDate today = LocalDate.now();
        try (XlsxReport wb = new XlsxReport("Gratuity dates")) {
            wb.header(SalaryCertificateService.COMPANY_NAME, "Joining vs Gratuity Date",
                    (onlyDifferent ? "Only employees whose gratuity is not counted from the joining date" : "All active employees")
                            + (departmentId == null ? "" : "   |   Department: " + departmentName(rows, departmentId)),
                    generated(rows.size()), COLS, WIDTHS);
            int n = 0;
            for (Row r : rows) {
                LocalDate doj = r.who().joiningDate();
                LocalDate from = r.countedFrom();
                wb.row(++n, r.who().employeeCode(), r.who().fullName(),
                        r.display() == null ? null : r.display().departmentName(),
                        r.display() == null ? null : r.display().designationTitle(),
                        date(doj), date(r.s().getHireDate()), date(r.s().getGratuityStartDate()), date(from),
                        doj == null || from == null ? null : ChronoUnit.DAYS.between(doj, from),
                        from == null || from.isAfter(today) ? null : service(from, today));
            }
            wb.totalRow("", "Total", n + " employee(s)");
            wb.write(out);
        }
    }

    private static String service(LocalDate from, LocalDate to) {
        Period p = Period.between(from, to);
        return p.getYears() + "y " + p.getMonths() + "m";
    }

    private static String departmentName(List<Row> rows, long departmentId) {
        return rows.stream().map(Row::display).filter(d -> d != null && d.departmentName() != null)
                .map(EmployeeDisplay::departmentName).findFirst().orElse("#" + departmentId);
    }

    private static int compareCards(String a, String b) {
        if (a.matches("\\d{1,18}") && b.matches("\\d{1,18}")) return Long.compare(Long.parseLong(a), Long.parseLong(b));
        return a.compareToIgnoreCase(b);
    }

    private static String date(LocalDate d) {
        return d == null ? null : DATE.format(d);
    }

    private static String generated(int count) {
        CurrentUser actor = CurrentUserHolder.get().orElse(null);
        return "Generated " + STAMP.format(LocalDateTime.now()) + " by "
                + (actor == null ? CurrentUser.SYSTEM_USERNAME : actor.username()) + "   |   " + count + " employee(s)";
    }
}
