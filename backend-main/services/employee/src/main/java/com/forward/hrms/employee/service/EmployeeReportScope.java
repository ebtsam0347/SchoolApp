package com.forward.hrms.employee.service;

import com.forward.hrms.employee.dto.request.EmployeeReportFilter;
import com.forward.hrms.employee.entity.Department;
import com.forward.hrms.employee.entity.Designation;
import com.forward.hrms.employee.entity.Employee;
import com.forward.hrms.employee.entity.Section;
import com.forward.hrms.employee.repository.DepartmentRepository;
import com.forward.hrms.employee.repository.DesignationRepository;
import com.forward.hrms.employee.repository.EmployeeRepository;
import com.forward.hrms.employee.repository.SectionRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Applies {@link EmployeeReportFilter} (+ manager scoping) for the
 * list-shaped employee reports, and renders the filter as the one-line
 * "Filters: …" caption printed under each report's title.
 */
@Service
@RequiredArgsConstructor
public class EmployeeReportScope {

    private final EmployeeRepository employees;
    private final DepartmentRepository departments;
    private final SectionRepository sections;
    private final DesignationRepository designations;

    /**
     * @param restrictToManagerId non-null → only that manager's direct reports
     * @return matching employees ordered by card number
     */
    public List<Employee> select(EmployeeReportFilter filter, Long restrictToManagerId) {
        EmployeeReportFilter f = filter == null ? EmployeeReportFilter.active() : filter;
        return employees.findAll().stream()
                .filter(e -> restrictToManagerId == null || restrictToManagerId.equals(e.getManagerId()))
                .filter(f::matches)
                .sorted(Comparator.comparing(Employee::getEmployeeCode,
                        Comparator.nullsLast(EmployeeReportFilter::compareCards)))
                .toList();
    }

    public String describe(EmployeeReportFilter filter, Long restrictToManagerId) {
        EmployeeReportFilter f = filter == null ? EmployeeReportFilter.active() : filter;
        List<String> parts = new ArrayList<>();
        parts.add("Status: " + switch (f.statusOrDefault()) {
            case ACTIVE -> "Active";
            case INACTIVE -> "Left";
            case ALL -> "All";
        });
        if (f.departmentId() != null) {
            parts.add("Department: " + departments.findById(f.departmentId()).map(Department::getName).orElse("#" + f.departmentId()));
        }
        if (f.sectionId() != null) {
            parts.add("Section: " + sections.findById(f.sectionId()).map(Section::getName).orElse("#" + f.sectionId()));
        }
        if (f.designationId() != null) {
            parts.add("Designation: " + designations.findById(f.designationId()).map(Designation::getTitle).orElse("#" + f.designationId()));
        }
        boolean from = f.cardFrom() != null && !f.cardFrom().isBlank();
        boolean to = f.cardTo() != null && !f.cardTo().isBlank();
        if (from || to) parts.add("Cards: " + (from ? f.cardFrom().trim() : "…") + " to " + (to ? f.cardTo().trim() : "…"));
        if (!f.cardList().isEmpty()) parts.add("Selected cards: " + f.cardList().size());
        if (restrictToManagerId != null) parts.add("Direct reports only");
        return String.join("   |   ", parts);
    }
}
