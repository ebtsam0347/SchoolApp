package com.forward.hrms.employee.service;

import com.forward.hrms.commons.core.error.UnprocessableError;
import com.forward.hrms.employee.config.EmployeeCacheConfig;
import com.forward.hrms.employee.dto.response.AggregateDto;
import com.forward.hrms.employee.entity.Department;
import com.forward.hrms.employee.entity.Designation;
import com.forward.hrms.employee.entity.Grade;
import com.forward.hrms.employee.entity.Section;
import com.forward.hrms.employee.enums.EmployeeErrorCode;
import com.forward.hrms.employee.repository.DepartmentRepository;
import com.forward.hrms.employee.repository.DesignationRepository;
import com.forward.hrms.employee.repository.EmployeeRepository;
import com.forward.hrms.employee.repository.GradeRepository;
import com.forward.hrms.employee.repository.SectionRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Headcount aggregations for the People-overview charts. One groupBy at a
 * time; a UI that wants four breakdowns issues four parallel calls (the
 * BFF stats composite does exactly this).
 */
@Service
@RequiredArgsConstructor
public class AggregateService {

    private final EmployeeRepository employees;
    private final DepartmentRepository departments;
    private final SectionRepository sections;
    private final GradeRepository grades;
    private final DesignationRepository designations;

    @Transactional(readOnly = true)
    public AggregateDto aggregate(String groupBy) {
        if (groupBy == null || groupBy.isBlank()) {
            throw new UnprocessableError(EmployeeErrorCode.VALIDATION_FAILED, "groupBy is required");
        }
        return switch (groupBy.toLowerCase()) {
            case "status" -> shape("status", employees.countByEmploymentStatus(), null);
            case "employmenttype" -> shape("employmentType", employees.countByEmploymentType(), null);
            case "gender" -> shape("gender", employees.countByGender(), null);
            case "department" -> shape("department", employees.countByDepartment(), departmentLabels());
            case "section" -> shape("section", employees.countBySection(), sectionLabels());
            case "grade" -> shape("grade", employees.countByGrade(), gradeLabels());
            case "designation" -> shape("designation", employees.countByDesignation(), designationLabels());
            default -> throw new UnprocessableError(EmployeeErrorCode.VALIDATION_FAILED,
                    "groupBy must be one of: status, employmentType, gender, " +
                            "department, section, grade, designation");
        };
    }

    private static AggregateDto shape(String groupBy, List<Object[]> rows, Map<Long, String> labels) {
        long total = 0L;
        var buckets = new java.util.ArrayList<AggregateDto.Bucket>(rows.size());
        for (Object[] r : rows) {
            String key = r[0] == null ? null : r[0].toString();
            long count = ((Number) r[1]).longValue();
            String label = labels != null && r[0] instanceof Long id ? labels.get(id) : null;
            buckets.add(new AggregateDto.Bucket(key, label, count));
            total += count;
        }
        return new AggregateDto(groupBy, total, buckets);
    }

    @Cacheable(cacheNames = EmployeeCacheConfig.CACHE_CATALOG_LABELS, key = "'department'")
    public Map<Long, String> departmentLabels() {
        Map<Long, String> out = new HashMap<>();
        for (Department d : departments.findAll()) out.put(d.getId(), d.getName());
        return out;
    }

    @Cacheable(cacheNames = EmployeeCacheConfig.CACHE_CATALOG_LABELS, key = "'section'")
    public Map<Long, String> sectionLabels() {
        Map<Long, String> out = new HashMap<>();
        for (Section s : sections.findAll()) out.put(s.getId(), s.getName());
        return out;
    }

    @Cacheable(cacheNames = EmployeeCacheConfig.CACHE_CATALOG_LABELS, key = "'grade'")
    public Map<Long, String> gradeLabels() {
        Map<Long, String> out = new HashMap<>();
        for (Grade g : grades.findAll()) out.put(g.getId(), g.getName());
        return out;
    }

    @Cacheable(cacheNames = EmployeeCacheConfig.CACHE_CATALOG_LABELS, key = "'designation'")
    public Map<Long, String> designationLabels() {
        Map<Long, String> out = new HashMap<>();
        for (Designation d : designations.findAll()) out.put(d.getId(), d.getTitle());
        return out;
    }
}
