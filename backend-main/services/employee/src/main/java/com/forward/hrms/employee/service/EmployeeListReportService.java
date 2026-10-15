package com.forward.hrms.employee.service;

import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.reports.XlsxReport;
import com.forward.hrms.employee.dto.request.EmployeeReportFilter;
import com.forward.hrms.employee.entity.City;
import com.forward.hrms.employee.entity.Department;
import com.forward.hrms.employee.entity.Designation;
import com.forward.hrms.employee.entity.Employee;
import com.forward.hrms.employee.entity.EmployeeExit;
import com.forward.hrms.employee.entity.EmployeeHistory;
import com.forward.hrms.employee.entity.EmployeeQualification;
import com.forward.hrms.employee.entity.Grade;
import com.forward.hrms.employee.entity.Qualification;
import com.forward.hrms.employee.entity.Section;
import com.forward.hrms.employee.enums.EmployeeListColumns;
import com.forward.hrms.employee.enums.EmploymentStatus;
import com.forward.hrms.employee.repository.CityRepository;
import com.forward.hrms.employee.repository.DepartmentRepository;
import com.forward.hrms.employee.repository.DesignationRepository;
import com.forward.hrms.employee.repository.EmployeeExitRepository;
import com.forward.hrms.employee.repository.EmployeeHistoryRepository;
import com.forward.hrms.employee.repository.EmployeeQualificationRepository;
import com.forward.hrms.employee.repository.GradeRepository;
import com.forward.hrms.employee.repository.QualificationRepository;
import com.forward.hrms.employee.repository.SectionRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Employee List (Excel) — one row per employee matching
 * {@link EmployeeReportFilter}. Replaces the legacy Employee List, Employee
 * List with All Info (minus salary/bank, which live in payroll), Missing Pic
 * List, Age Report and Seniority List: {@code columns=FULL} adds age and
 * total service, {@code missingPhoto=true} keeps only employees without a
 * photo, and the sheet's autofilter/sort covers seniority.
 */
@Service
@RequiredArgsConstructor
public class EmployeeListReportService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm", Locale.ENGLISH);

    private static final String[] BASIC = {"#", "Card No", "Name", "Father Name", "CNIC", "Department", "Section",
            "Designation", "Qualification", "Category", "Grade", "Grade Effective", "Gender", "Date of Birth",
            "Date of Joining", "Blood Group", "Leave Date", "Status"};
    private static final int[] BASIC_W = {5, 10, 26, 24, 17, 22, 20, 22, 18, 11, 8, 13, 9, 13, 13, 8, 13, 11};
    private static final String[] FULL = {"Spouse Name", "Mother Name", "Age", "Marital Status", "Total Service",
            "CNIC Issue", "CNIC Expiry", "Religion", "City", "Specialization", "Mobile", "Personal Email",
            "Work Email", "Permanent Address", "Current Address", "EOBI No", "SSC No", "Reference", "Photo"};
    private static final int[] FULL_W = {22, 22, 6, 12, 12, 13, 13, 11, 14, 22, 14, 26, 26, 40, 40, 14, 14, 20, 7};

    private final EmployeeReportScope scope;
    private final DepartmentRepository departments;
    private final SectionRepository sections;
    private final DesignationRepository designations;
    private final GradeRepository grades;
    private final CityRepository cities;
    private final QualificationRepository qualificationCatalog;
    private final EmployeeQualificationRepository qualifications;
    private final EmployeeHistoryRepository history;
    private final EmployeeExitRepository exits;
    private final LetterSettingsService letterSettings;
    private final AuditEmitter audit;

    @Transactional
    public void writeXlsx(OutputStream out, EmployeeReportFilter filter, EmployeeListColumns columns,
                          boolean missingPhoto, Long restrictToManagerId) throws IOException {
        EmployeeListColumns cols = columns == null ? EmployeeListColumns.BASIC : columns;
        List<Employee> rows = scope.select(filter, restrictToManagerId).stream()
                .filter(e -> !missingPhoto || blank(e.getProfilePictureKey()))
                .toList();

        Map<Long, Department> deptById = byId(departments.findAll(), Department::getId);
        Map<Long, Section> sectionById = byId(sections.findAll(), Section::getId);
        Map<Long, Designation> desigById = byId(designations.findAll(), Designation::getId);
        Map<Long, Grade> gradeById = byId(grades.findAll(), Grade::getId);
        Map<Long, City> cityById = byId(cities.findAll(), City::getId);
        Map<Long, Qualification> qualById = byId(qualificationCatalog.findAll(), Qualification::getId);
        Map<Long, EmployeeQualification> highest = highestQualification();
        Map<Long, List<EmployeeHistory>> historyByEmp = history.findAll().stream()
                .collect(Collectors.groupingBy(EmployeeHistory::getEmployeeId));
        Map<Long, EmployeeExit> lastExit = new HashMap<>();
        for (EmployeeExit x : exits.findAll()) {
            lastExit.merge(x.getEmployeeId(), x, (a, b) -> a.getId() > b.getId() ? a : b);
        }

        String[] headers = cols == EmployeeListColumns.FULL ? concat(BASIC, FULL) : BASIC;
        int[] widths = cols == EmployeeListColumns.FULL ? concat(BASIC_W, FULL_W) : BASIC_W;
        String title = "Employee List" + (cols == EmployeeListColumns.FULL ? " — All Information" : "")
                + (missingPhoto ? " — Missing Photo" : "");

        try (XlsxReport wb = new XlsxReport("Employees")) {
            wb.header(letterSettings.current().getCompanyLegalName(), title,
                    "Filters: " + scope.describe(filter, restrictToManagerId), generatedLine(rows.size()),
                    headers, widths);
            int n = 0;
            LocalDate today = LocalDate.now();
            for (Employee e : rows) {
                EmployeeQualification eq = highest.get(e.getId());
                Qualification q = eq == null || eq.getQualificationId() == null ? null : qualById.get(eq.getQualificationId());
                Designation desig = e.getDesignationId() == null ? null : desigById.get(e.getDesignationId());
                Grade grade = e.getGradeId() == null ? null : gradeById.get(e.getGradeId());
                EmployeeExit exit = e.getEmploymentStatus() == EmploymentStatus.TERMINATED ? lastExit.get(e.getId()) : null;

                List<Object> v = new ArrayList<>(headers.length);
                v.add(++n);
                v.add(e.getEmployeeCode());
                v.add(e.getFullName());
                v.add(e.getFatherName());
                v.add(e.getCnic());
                v.add(name(deptById.get(e.getDepartmentId()), Department::getName));
                v.add(name(sectionById.get(e.getSectionId()), Section::getName));
                v.add(desig == null ? null : desig.getTitle());
                v.add(q == null ? null : q.getLevel());
                v.add(desig == null || desig.getCategory() == null ? null : pretty(desig.getCategory().name()));
                v.add(grade == null ? null : grade.getCode());
                v.add(date(gradeEffective(e, historyByEmp.getOrDefault(e.getId(), List.of()))));
                v.add(pretty(e.getGender()));
                v.add(date(e.getDateOfBirth()));
                v.add(date(e.getJoiningDate()));
                v.add(e.getBloodGroup());
                v.add(exit == null ? null : date(exit.getLastWorkingDay()));
                v.add(pretty(e.getEmploymentStatus() == null ? null : e.getEmploymentStatus().name()));
                if (cols == EmployeeListColumns.FULL) {
                    v.add(e.getSpouseName());
                    v.add(e.getMotherName());
                    v.add(e.getDateOfBirth() == null ? null : Period.between(e.getDateOfBirth(), today).getYears());
                    v.add(pretty(e.getMaritalStatus()));
                    v.add(service(e.getJoiningDate(), exit == null ? today : exit.getLastWorkingDay()));
                    v.add(date(e.getCnicIssueDate()));
                    v.add(date(e.getCnicExpiryDate()));
                    v.add(e.getReligion());
                    v.add(name(cityById.get(e.getCityId()), City::getName));
                    v.add(q == null ? null : q.getName());
                    v.add(e.getPhone());
                    v.add(e.getPersonalEmail());
                    v.add(e.getWorkEmail());
                    v.add(e.getPermanentAddress());
                    v.add(e.getCurrentAddress());
                    v.add(e.getEobiNumber());
                    v.add(e.getSscNumber());
                    v.add(e.getReferenceBy());
                    v.add(blank(e.getProfilePictureKey()) ? "No" : "Yes");
                }
                wb.row(v.toArray());
            }
            wb.totalRow("", "Total", rows.size() + " employee(s)");
            wb.write(out);
        }
        audit.emit("report", "0", "employee-list.run", null,
                Map.of("columns", cols.name(), "missingPhoto", missingPhoto, "rowCount", rows.size()), false);
    }

    // ---------- derived values ----------

    /** Highest qualification per employee: latest year of completion, ties by lowest id. */
    private Map<Long, EmployeeQualification> highestQualification() {
        Comparator<EmployeeQualification> better = Comparator
                .comparing(EmployeeQualification::getYearOfCompletion, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(EmployeeQualification::getId, Comparator.reverseOrder());
        Map<Long, EmployeeQualification> m = new HashMap<>();
        for (EmployeeQualification q : qualifications.findAll()) {
            m.merge(q.getEmployeeId(), q, (a, b) -> better.compare(a, b) >= 0 ? a : b);
        }
        return m;
    }

    /** Start of the unbroken run of history rows carrying the employee's current grade. */
    static LocalDate gradeEffective(Employee e, List<EmployeeHistory> rows) {
        if (e.getGradeId() == null || rows.isEmpty()) return null;
        List<EmployeeHistory> sorted = rows.stream()
                .sorted(Comparator.comparing(EmployeeHistory::getEffectiveFrom, Comparator.nullsFirst(Comparator.naturalOrder())))
                .toList();
        LocalDate since = null;
        for (int i = sorted.size() - 1; i >= 0; i--) {
            if (!e.getGradeId().equals(sorted.get(i).getGradeId())) break;
            since = sorted.get(i).getEffectiveFrom();
        }
        return since;
    }

    private static String service(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) return null;
        Period p = Period.between(from, to);
        return p.getYears() + "y " + p.getMonths() + "m";
    }

    private static String generatedLine(int count) {
        CurrentUser actor = CurrentUserHolder.get().orElse(null);
        return "Generated " + STAMP.format(LocalDateTime.now()) + " by "
                + (actor == null ? CurrentUser.SYSTEM_USERNAME : actor.username()) + "   |   " + count + " employee(s)";
    }

    // ---------- formatting ----------

    private static <T> String name(T entity, Function<T, String> fn) {
        return entity == null ? null : fn.apply(entity);
    }

    private static String date(LocalDate d) {
        return d == null ? null : DATE.format(d);
    }

    static String pretty(String raw) {
        if (blank(raw)) return null;
        StringBuilder sb = new StringBuilder();
        for (String w : raw.trim().toLowerCase(Locale.ROOT).split("[_\\s]+")) {
            if (w.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String[] concat(String[] a, String[] b) {
        String[] r = new String[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }

    private static int[] concat(int[] a, int[] b) {
        int[] r = new int[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }

    private static <T> Map<Long, T> byId(List<T> rows, Function<T, Long> idFn) {
        Map<Long, T> m = new HashMap<>(rows.size());
        for (T t : rows) m.put(idFn.apply(t), t);
        return m;
    }
}
