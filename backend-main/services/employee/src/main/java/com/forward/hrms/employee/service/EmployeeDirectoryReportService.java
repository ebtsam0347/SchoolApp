package com.forward.hrms.employee.service;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.commons.reports.CsvWriter;
import com.forward.hrms.employee.entity.Department;
import com.forward.hrms.employee.entity.Designation;
import com.forward.hrms.employee.entity.Employee;
import com.forward.hrms.employee.entity.Grade;
import com.forward.hrms.employee.entity.Section;
import com.forward.hrms.employee.enums.EmploymentStatus;
import com.forward.hrms.employee.enums.EmploymentType;
import com.forward.hrms.employee.repository.DepartmentRepository;
import com.forward.hrms.employee.repository.DesignationRepository;
import com.forward.hrms.employee.repository.EmployeeRepository;
import com.forward.hrms.employee.repository.GradeRepository;
import com.forward.hrms.employee.repository.SectionRepository;

import lombok.RequiredArgsConstructor;


@Service
@RequiredArgsConstructor
public class EmployeeDirectoryReportService {

    private final EmployeeRepository employees;
    private final DepartmentRepository departments;
    private final SectionRepository sections;
    private final DesignationRepository designations;
    private final GradeRepository grades;
    private final AuditEmitter audit;

    private static final List<String> HEADERS = List.of(
            "Employee Code", "Full Name", "Work Email", "Personal Email", "Phone",
            "CNIC", "Department", "Section", "Designation", "Grade", "Manager Code",
            "Joining Date", "Confirmation Date", "Employment Status", "Employment Type");

    @Transactional(readOnly = true)
    public void writeCsv(OutputStream out, Long restrictToManagerId) throws IOException {
        List<Employee> rows = load(restrictToManagerId);

        Map<Long, Department> deptById = byId(departments.findAll(), Department::getId);
        Map<Long, Section> sectionById = byId(sections.findAll(), Section::getId);
        Map<Long, Designation> desigById = byId(designations.findAll(), Designation::getId);
        Map<Long, Grade> gradeById = byId(grades.findAll(), Grade::getId);
        Map<Long, String> managerCodeById = loadManagerCodes(rows);

        OutputStreamWriter writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
        CsvWriter csv = new CsvWriter(writer);
        csv.writeBom();
        csv.writeRow(HEADERS);
        for (Employee e : rows) {
            Department d = e.getDepartmentId() == null ? null : deptById.get(e.getDepartmentId());
            Section s = e.getSectionId() == null ? null : sectionById.get(e.getSectionId());
            Designation des = e.getDesignationId() == null ? null : desigById.get(e.getDesignationId());
            Grade g = e.getGradeId() == null ? null : gradeById.get(e.getGradeId());
            csv.writeRow(
                    e.getEmployeeCode(),
                    e.getFullName(),
                    e.getWorkEmail(),
                    e.getPersonalEmail(),
                    e.getPhone(),
                    e.getCnic(),
                    d == null ? "" : d.getName(),
                    s == null ? "" : s.getName(),
                    des == null ? "" : des.getTitle(),
                    g == null ? "" : g.getCode(),
                    managerCodeById.getOrDefault(e.getId(), ""),
                    formatDate(e.getJoiningDate()),
                    formatDate(e.getConfirmationDate()),
                    e.getEmploymentStatus(),
                    e.getEmploymentType());
        }
        csv.flush();

        audit.emit("report", "0", "employee-directory.run",
                null,
                Map.of(
                        "format", "csv",
                        "restrictToManagerId", restrictToManagerId == null ? "" : restrictToManagerId,
                        "rowCount", rows.size()),
                false);
    }

    private List<Employee> load(Long restrictToManagerId) {
        if (restrictToManagerId != null) {
            return employees.findAllByManagerId(restrictToManagerId).stream()
                    .filter(e -> e.getEmploymentStatus() != EmploymentStatus.TERMINATED)
                    .sorted((a, b) -> a.getEmployeeCode().compareTo(b.getEmployeeCode()))
                    .toList();
        }
        return employees.findAll(Sort.by("employeeCode").ascending()).stream()
                .filter(e -> e.getEmploymentStatus() != EmploymentStatus.TERMINATED)
                .toList();
    }

    private Map<Long, String> loadManagerCodes(List<Employee> rows) {
        Set<Long> ids = new HashSet<>();
        for (Employee e : rows) {
            if (e.getManagerId() != null) ids.add(e.getManagerId());
        }
        if (ids.isEmpty()) return Map.of();
        Map<Long, String> out = new HashMap<>(ids.size());
        for (Employee m : employees.findAllByIdIn(ids)) {
            out.put(m.getId(), m.getEmployeeCode());
        }
        // Map by reportee id rather than manager id for direct row lookup.
        Map<Long, String> byReportee = new HashMap<>(rows.size());
        for (Employee e : rows) {
            if (e.getManagerId() != null) {
                String code = out.get(e.getManagerId());
                if (code != null) byReportee.put(e.getId(), code);
            }
        }
        return byReportee;
    }

    private static <T> Map<Long, T> byId(List<T> list, java.util.function.Function<T, Long> idFn) {
        Map<Long, T> out = new HashMap<>(list.size());
        for (T item : list) out.put(idFn.apply(item), item);
        return out;
    }

    private static String formatDate(LocalDate d) {
        return d == null ? "" : d.toString();
    }

    @SuppressWarnings("unused")
    private static String formatType(EmploymentType t) { return t == null ? "" : t.name(); }
}
