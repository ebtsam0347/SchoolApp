package com.forward.hrms.employee.service;

import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.reports.JasperRenderer;
import com.forward.hrms.employee.dto.request.EmployeeReportFilter;
import com.forward.hrms.employee.entity.Department;
import com.forward.hrms.employee.entity.Designation;
import com.forward.hrms.employee.entity.Employee;
import com.forward.hrms.employee.entity.EmployeeQualification;
import com.forward.hrms.employee.entity.Grade;
import com.forward.hrms.employee.entity.LetterSettings;
import com.forward.hrms.employee.entity.Qualification;
import com.forward.hrms.employee.enums.DepartmentWiseGroupBy;
import com.forward.hrms.employee.repository.DepartmentRepository;
import com.forward.hrms.employee.repository.DesignationRepository;
import com.forward.hrms.employee.repository.EmployeeQualificationRepository;
import com.forward.hrms.employee.repository.GradeRepository;
import com.forward.hrms.employee.repository.QualificationRepository;

import lombok.RequiredArgsConstructor;

import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Department Wise (PDF) — each department's headcount broken down by one
 * attribute (grade, employment type, category, gender, marital status,
 * religion, blood group, qualification, age band or service band), split
 * by gender with the value's share of the department. A final "All
 * Departments" block gives the company-wide breakdown. Replaces the legacy
 * Department Wise reports and Labour/Supervisor Grade (grade + category).
 */
@Service
@RequiredArgsConstructor
public class DepartmentWiseReportService {

    static final String NOT_SET = "(Not set)";
    static final String ALL_DEPARTMENTS = "All Departments";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH);
    private static final List<String> AGE_BANDS = List.of("Under 20", "20-29", "30-39", "40-49", "50-59", "60+");
    private static final List<String> SERVICE_BANDS = List.of("Under 1 year", "1-2 years", "3-5 years", "6-10 years",
            "11-20 years", "Over 20 years");

    /** Alphabetical, bands in their natural order, "(Not set)" last. */
    private static final Comparator<String> VALUE_ORDER = (a, b) -> {
        if (a.equals(b)) return 0;
        if (NOT_SET.equals(a)) return 1;
        if (NOT_SET.equals(b)) return -1;
        int ia = band(a), ib = band(b);
        if (ia >= 0 && ib >= 0) return Integer.compare(ia, ib);
        return a.compareToIgnoreCase(b);
    };

    private final EmployeeReportScope scope;
    private final DepartmentRepository departments;
    private final DesignationRepository designations;
    private final GradeRepository grades;
    private final QualificationRepository qualificationCatalog;
    private final EmployeeQualificationRepository qualifications;
    private final LetterSettingsService letterSettings;
    private final JasperRenderer jasper;
    private final AuditEmitter audit;

    @Transactional
    public byte[] render(EmployeeReportFilter filter, DepartmentWiseGroupBy groupBy, Long restrictToManagerId) {
        DepartmentWiseGroupBy by = groupBy == null ? DepartmentWiseGroupBy.GRADE : groupBy;
        List<Employee> people = scope.select(filter, restrictToManagerId);
        Function<Employee, String> valueOf = classifier(by);
        Map<Long, Department> deptById = byId(departments.findAll(), Department::getId);

        // department → value → [male, female, other]
        Map<String, Map<String, int[]>> tree = new TreeMap<>(VALUE_ORDER);
        Map<String, int[]> company = new TreeMap<>(VALUE_ORDER);
        int male = 0, female = 0, other = 0;
        for (Employee e : people) {
            Department d = e.getDepartmentId() == null ? null : deptById.get(e.getDepartmentId());
            String dept = d == null ? NOT_SET : d.getName();
            String value = valueOf.apply(e);
            int g = genderIndex(e.getGender());
            tree.computeIfAbsent(dept, k -> new TreeMap<>(VALUE_ORDER)).computeIfAbsent(value, k -> new int[3])[g]++;
            company.computeIfAbsent(value, k -> new int[3])[g]++;
            switch (g) {
                case 0 -> male++;
                case 1 -> female++;
                default -> other++;
            }
        }

        List<Map<String, ?>> rows = new ArrayList<>();
        tree.forEach((dept, values) -> addBlock(rows, dept, values));
        if (tree.size() > 1) addBlock(rows, ALL_DEPARTMENTS, company);

        LetterSettings settings = letterSettings.current();
        CurrentUser actor = CurrentUserHolder.get().orElse(null);
        Map<String, Object> params = new HashMap<>();
        params.put("companyName", settings.getCompanyLegalName());
        params.put("companyAddress", settings.getCompanyAddress());
        params.put("reportTitle", "Department Wise — " + by.label());
        params.put("reportPeriod", "As of " + DATE.format(LocalDate.now()) + "   |   " + scope.describe(filter, restrictToManagerId));
        params.put("valueHeading", by.label());
        params.put("totalMale", male);
        params.put("totalFemale", female);
        params.put("totalOther", other);
        params.put("totalAll", people.size());
        params.put("generatedAt", STAMP.format(LocalDateTime.now()));
        params.put("generatedBy", actor == null ? CurrentUser.SYSTEM_USERNAME : actor.username());

        byte[] pdf = jasper.renderPdf("/jasper/department-wise.jrxml", params, new JRMapCollectionDataSource(rows));
        audit.emit("report", "0", "department-wise.run", null,
                Map.of("groupBy", by.name(), "total", people.size()), false);
        return pdf;
    }

    private static void addBlock(List<Map<String, ?>> rows, String dept, Map<String, int[]> values) {
        int deptTotal = values.values().stream().mapToInt(c -> c[0] + c[1] + c[2]).sum();
        values.forEach((value, c) -> {
            int total = c[0] + c[1] + c[2];
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("department", dept);
            m.put("value", value);
            m.put("male", c[0]);
            m.put("female", c[1]);
            m.put("other", c[2]);
            m.put("total", total);
            m.put("share", deptTotal == 0 ? "" : "%.1f%%".formatted(total * 100.0 / deptTotal));
            rows.add(m);
        });
    }

    private Function<Employee, String> classifier(DepartmentWiseGroupBy by) {
        LocalDate today = LocalDate.now();
        return switch (by) {
            case GRADE -> {
                Map<Long, Grade> g = byId(grades.findAll(), Grade::getId);
                yield e -> e.getGradeId() == null || !g.containsKey(e.getGradeId()) ? NOT_SET : g.get(e.getGradeId()).getCode();
            }
            case EMPLOYMENT_TYPE -> e -> e.getEmploymentType() == null ? NOT_SET : or(EmployeeListReportService.pretty(e.getEmploymentType().name()));
            case CATEGORY -> {
                Map<Long, Designation> d = byId(designations.findAll(), Designation::getId);
                yield e -> {
                    Designation x = e.getDesignationId() == null ? null : d.get(e.getDesignationId());
                    return x == null || x.getCategory() == null ? NOT_SET : or(EmployeeListReportService.pretty(x.getCategory().name()));
                };
            }
            case GENDER -> e -> or(EmployeeListReportService.pretty(e.getGender()));
            case MARITAL_STATUS -> e -> or(EmployeeListReportService.pretty(e.getMaritalStatus()));
            case RELIGION -> e -> or(EmployeeListReportService.pretty(e.getReligion()));
            case BLOOD_GROUP -> e -> e.getBloodGroup() == null || e.getBloodGroup().isBlank() ? NOT_SET
                    : e.getBloodGroup().trim().toUpperCase(Locale.ROOT);
            case QUALIFICATION -> {
                Map<Long, Qualification> catalog = byId(qualificationCatalog.findAll(), Qualification::getId);
                Map<Long, String> level = new HashMap<>();
                Map<Long, Integer> year = new HashMap<>();
                for (EmployeeQualification q : qualifications.findAll()) {
                    Qualification c = q.getQualificationId() == null ? null : catalog.get(q.getQualificationId());
                    if (c == null || c.getLevel() == null) continue;
                    int y = q.getYearOfCompletion() == null ? Integer.MIN_VALUE : q.getYearOfCompletion();
                    if (!year.containsKey(q.getEmployeeId()) || y > year.get(q.getEmployeeId())) {
                        year.put(q.getEmployeeId(), y);
                        level.put(q.getEmployeeId(), c.getLevel());
                    }
                }
                yield e -> level.getOrDefault(e.getId(), NOT_SET);
            }
            case AGE_BAND -> e -> {
                if (e.getDateOfBirth() == null) return NOT_SET;
                int age = Period.between(e.getDateOfBirth(), today).getYears();
                return age < 20 ? AGE_BANDS.get(0) : age >= 60 ? AGE_BANDS.get(5) : AGE_BANDS.get(age / 10 - 1);
            };
            case SERVICE_BAND -> e -> {
                if (e.getJoiningDate() == null) return NOT_SET;
                int years = Period.between(e.getJoiningDate(), today).getYears();
                return years < 1 ? SERVICE_BANDS.get(0) : years <= 2 ? SERVICE_BANDS.get(1) : years <= 5 ? SERVICE_BANDS.get(2)
                        : years <= 10 ? SERVICE_BANDS.get(3) : years <= 20 ? SERVICE_BANDS.get(4) : SERVICE_BANDS.get(5);
            };
        };
    }

    /** Index in the age or service bands (they don't overlap), -1 otherwise. */
    private static int band(String s) {
        int i = AGE_BANDS.indexOf(s);
        return i >= 0 ? i : SERVICE_BANDS.indexOf(s);
    }

    private static int genderIndex(String gender) {
        String g = gender == null ? "" : gender.trim().toUpperCase(Locale.ROOT);
        return switch (g) {
            case "MALE", "M" -> 0;
            case "FEMALE", "F" -> 1;
            default -> 2;
        };
    }

    private static String or(String s) {
        return s == null ? NOT_SET : s;
    }

    private static <T> Map<Long, T> byId(List<T> rows, Function<T, Long> idFn) {
        Map<Long, T> m = new HashMap<>(rows.size());
        for (T t : rows) m.put(idFn.apply(t), t);
        return m;
    }
}
