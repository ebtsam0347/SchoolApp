package com.forward.hrms.employee.service;

import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.reports.JasperRenderer;
import com.forward.hrms.employee.entity.Department;
import com.forward.hrms.employee.entity.Designation;
import com.forward.hrms.employee.entity.Employee;
import com.forward.hrms.employee.entity.LetterSettings;
import com.forward.hrms.employee.entity.Section;
import com.forward.hrms.employee.enums.EmploymentStatus;
import com.forward.hrms.employee.repository.DepartmentRepository;
import com.forward.hrms.employee.repository.DesignationRepository;
import com.forward.hrms.employee.repository.EmployeeRepository;
import com.forward.hrms.employee.repository.SectionRepository;

import lombok.RequiredArgsConstructor;

import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Department Strength — current (non-terminated) headcount by
 * department → section → designation, split by gender
 * ({@code jasper/department-strength.jrxml}). Complements the headcount
 * snapshot (department × grade) with the org-structure view HR uses for
 * manpower planning. Rows are pre-aggregated here; the template groups by
 * department and section for subtotals.
 */
@Service
@RequiredArgsConstructor
public class DepartmentStrengthReportService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MMM-yyyy");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm");
    static final String UNASSIGNED = "(Unassigned)";

    /** Alphabetical, with "(Unassigned)" pushed to the end of its level. */
    private static final Comparator<String> UNASSIGNED_LAST = (a, b) -> {
        if (a.equals(b)) return 0;
        if (UNASSIGNED.equals(a)) return 1;
        if (UNASSIGNED.equals(b)) return -1;
        int c = a.compareToIgnoreCase(b);
        return c != 0 ? c : a.compareTo(b);
    };

    private final EmployeeRepository employees;
    private final DepartmentRepository departments;
    private final SectionRepository sections;
    private final DesignationRepository designations;
    private final LetterSettingsService letterSettings;
    private final JasperRenderer jasper;
    private final AuditEmitter audit;

    /** @param restrictToManagerId non-null → count only that manager's direct reports. */
    @Transactional
    public byte[] render(Long restrictToManagerId) {
        Map<Long, Department> deptById = byId(departments.findAll(), Department::getId);
        Map<Long, Section> sectionById = byId(sections.findAll(), Section::getId);
        Map<Long, Designation> desigById = byId(designations.findAll(), Designation::getId);

        List<Employee> active = employees.findAll().stream()
                .filter(e -> e.getEmploymentStatus() != EmploymentStatus.TERMINATED)
                .filter(e -> restrictToManagerId == null || restrictToManagerId.equals(e.getManagerId()))
                .toList();

        // department → section → designation → [male, female, not set]
        Map<String, Map<String, Map<String, int[]>>> tree = new TreeMap<>(UNASSIGNED_LAST);
        int male = 0, female = 0, other = 0;
        for (Employee e : active) {
            Department d = e.getDepartmentId() == null ? null : deptById.get(e.getDepartmentId());
            Section s = e.getSectionId() == null ? null : sectionById.get(e.getSectionId());
            Designation g = e.getDesignationId() == null ? null : desigById.get(e.getDesignationId());
            int[] counts = tree
                    .computeIfAbsent(d == null ? UNASSIGNED : d.getName(), k -> new TreeMap<>(UNASSIGNED_LAST))
                    .computeIfAbsent(s == null ? UNASSIGNED : s.getName(), k -> new TreeMap<>(UNASSIGNED_LAST))
                    .computeIfAbsent(g == null ? UNASSIGNED : g.getTitle(), k -> new int[3]);
            String gender = e.getGender() == null ? "" : e.getGender().trim().toUpperCase();
            switch (gender) {
                case "MALE", "M" -> { counts[0]++; male++; }
                case "FEMALE", "F" -> { counts[1]++; female++; }
                default -> { counts[2]++; other++; }
            }
        }

        List<Map<String, ?>> rows = new ArrayList<>();
        tree.forEach((dept, bySection) -> bySection.forEach((section, byDesig) -> byDesig.forEach((desig, c) -> {
            Map<String, Object> m = new HashMap<>();
            m.put("department", dept);
            m.put("section", section);
            m.put("designation", desig);
            m.put("male", c[0]);
            m.put("female", c[1]);
            m.put("other", c[2]);
            m.put("total", c[0] + c[1] + c[2]);
            rows.add(m);
        })));

        LetterSettings settings = letterSettings.current();
        CurrentUser actor = CurrentUserHolder.get().orElse(null);
        Map<String, Object> params = new HashMap<>();
        params.put("companyName", settings.getCompanyLegalName());
        params.put("companyAddress", settings.getCompanyAddress());
        params.put("reportTitle", "Department Strength");
        params.put("reportPeriod", "As of " + DATE.format(LocalDate.now())
                + (restrictToManagerId == null ? "" : " — direct reports only"));
        params.put("totalMale", male);
        params.put("totalFemale", female);
        params.put("totalOther", other);
        params.put("totalActive", active.size());
        params.put("generatedAt", STAMP.format(LocalDateTime.now()));
        params.put("generatedBy", actor == null ? CurrentUser.SYSTEM_USERNAME : actor.username());

        byte[] pdf = jasper.renderPdf("/jasper/department-strength.jrxml", params, new JRMapCollectionDataSource(rows));
        audit.emit("report", "0", "department-strength.run", null,
                Map.of("totalActive", active.size(), "managerScoped", restrictToManagerId != null), false);
        return pdf;
    }

    private static <T> Map<Long, T> byId(List<T> rows, Function<T, Long> idFn) {
        Map<Long, T> m = new HashMap<>(rows.size());
        for (T t : rows) m.put(idFn.apply(t), t);
        return m;
    }
}
