package com.forward.hrms.payroll.service;

import com.forward.hrms.commons.cache.EmployeeDisplay;
import com.forward.hrms.commons.cache.EmployeeDisplayCache;
import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.UnprocessableError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.reports.XlsxReport;
import com.forward.hrms.payroll.entity.EmployeeSalary;
import com.forward.hrms.payroll.enums.PayrollErrorCode;
import com.forward.hrms.payroll.repository.EmployeeSalaryRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Salary Increment List (Excel) — every basic-salary revision whose
 * effective date falls in the period: previous basic, new basic, the change
 * in rupees and percent, and the recorded reason. Covers batch increments
 * and one-off revisions alike, since both land in {@code employee_salaries}.
 * Replaces the legacy "Salary Increment Upgradation List" (by month, or by
 * month and department). Names come from the shared employee display cache.
 */
@Service
@RequiredArgsConstructor
public class IncrementListReportService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm", Locale.ENGLISH);
    private static final String[] COLS = {"#", "Effective From", "Card No", "Name", "Department", "Section",
            "Designation", "Previous Basic", "New Basic", "Increment", "Increment %", "Reason"};
    private static final int[] WIDTHS = {5, 13, 10, 26, 22, 20, 22, 14, 14, 13, 11, 30};

    private final EmployeeSalaryRepository salaries;
    private final EmployeeDisplayCache displays;
    private final AuditEmitter audit;

    record Revision(EmployeeSalary current, BigDecimal previousBasic, EmployeeDisplay who) {}

    /** The resolved period and rows; built on the request thread (the display cache may fall through to HTTP). */
    public record Loaded(LocalDate start, LocalDate end, Long departmentId, List<Revision> rows) {}

    /**
     * @param from default: first day of the current month
     * @param to   default: last day of {@code from}'s month
     */
    public void writeXlsx(OutputStream out, LocalDate from, LocalDate to, Long departmentId) throws IOException {
        write(out, load(from, to, departmentId));
    }

    @Transactional
    public Loaded load(LocalDate from, LocalDate to, Long departmentId) {
        LocalDate start = from == null ? YearMonth.now().atDay(1) : from;
        LocalDate end = to == null ? YearMonth.from(start).atEndOfMonth() : to;
        if (start.isAfter(end)) {
            throw new UnprocessableError(PayrollErrorCode.INVALID_PERIOD, "from must be on or before to");
        }
        List<Revision> rows = revisions(start, end, departmentId);
        audit.emit("report", "0", "increment-list.run", null,
                Map.of("from", start, "to", end, "rowCount", rows.size()), false);
        return new Loaded(start, end, departmentId, rows);
    }

    public void write(OutputStream out, Loaded loaded) throws IOException {
        LocalDate start = loaded.start(), end = loaded.end();
        Long departmentId = loaded.departmentId();
        List<Revision> rows = loaded.rows();
        try (XlsxReport wb = new XlsxReport("Increments")) {
            wb.header(SalaryCertificateService.COMPANY_NAME, "Salary Increment List",
                    "Period: " + DATE.format(start) + " to " + DATE.format(end)
                            + (departmentId == null ? "" : "   |   Department: " + departmentName(rows, departmentId)),
                    generatedLine(rows.size()), COLS, WIDTHS);
            int n = 0;
            BigDecimal totalIncrease = BigDecimal.ZERO;
            for (Revision r : rows) {
                BigDecimal now = r.current().getBasic();
                BigDecimal delta = now.subtract(r.previousBasic());
                totalIncrease = totalIncrease.add(delta);
                EmployeeDisplay d = r.who();
                wb.row(++n, DATE.format(r.current().getEffectiveFrom()),
                        d == null ? "#" + r.current().getEmployeeId() : d.employeeCode(),
                        d == null ? null : d.fullName(), d == null ? null : d.departmentName(),
                        d == null ? null : d.sectionName(), d == null ? null : d.designationTitle(),
                        r.previousBasic(), now, delta, percent(delta, r.previousBasic()),
                        r.current().getRevisionReason());
            }
            wb.totalRow("", "Total", "", n + " revision(s)", "", "", "", "", "", totalIncrease);
            wb.write(out);
        }
    }

    List<Revision> revisions(LocalDate start, LocalDate end, Long departmentId) {
        Map<Long, List<EmployeeSalary>> byEmp = salaries.findAll().stream()
                .filter(s -> s.getEffectiveFrom() != null && s.getBasic() != null)
                .collect(Collectors.groupingBy(EmployeeSalary::getEmployeeId));
        List<EmployeeSalary[]> pairs = new ArrayList<>();
        for (List<EmployeeSalary> list : byEmp.values()) {
            List<EmployeeSalary> sorted = list.stream()
                    .sorted(Comparator.comparing(EmployeeSalary::getEffectiveFrom).thenComparing(EmployeeSalary::getId))
                    .toList();
            for (int i = 1; i < sorted.size(); i++) {
                EmployeeSalary cur = sorted.get(i);
                if (cur.getEffectiveFrom().isBefore(start) || cur.getEffectiveFrom().isAfter(end)) continue;
                pairs.add(new EmployeeSalary[] {sorted.get(i - 1), cur});
            }
        }
        Map<Long, EmployeeDisplay> who = displays.getMany(pairs.stream().map(p -> p[1].getEmployeeId()).distinct().toList());
        return pairs.stream()
                .map(p -> new Revision(p[1], p[0].getBasic(), who.get(p[1].getEmployeeId())))
                .filter(r -> departmentId == null || (r.who() != null && departmentId.equals(r.who().departmentId())))
                .sorted(Comparator.comparing((Revision r) -> r.current().getEffectiveFrom())
                        .thenComparing(r -> r.who() == null ? "" : r.who().employeeCode(), IncrementListReportService::compareCards))
                .toList();
    }

    private static String departmentName(List<Revision> rows, long departmentId) {
        return rows.stream().map(Revision::who).filter(d -> d != null && d.departmentName() != null)
                .map(EmployeeDisplay::departmentName).findFirst().orElse("#" + departmentId);
    }

    private static BigDecimal percent(BigDecimal delta, BigDecimal base) {
        if (base == null || base.signum() == 0) return null;
        return delta.multiply(BigDecimal.valueOf(100)).divide(base, 2, RoundingMode.HALF_UP);
    }

    /** Numeric when both are numbers (legacy cards), else text. */
    private static int compareCards(String a, String b) {
        if (a.matches("\\d{1,18}") && b.matches("\\d{1,18}")) return Long.compare(Long.parseLong(a), Long.parseLong(b));
        return a.compareToIgnoreCase(b);
    }

    private static String generatedLine(int count) {
        CurrentUser actor = CurrentUserHolder.get().orElse(null);
        return "Generated " + STAMP.format(LocalDateTime.now()) + " by "
                + (actor == null ? CurrentUser.SYSTEM_USERNAME : actor.username()) + "   |   " + count + " revision(s)";
    }
}
