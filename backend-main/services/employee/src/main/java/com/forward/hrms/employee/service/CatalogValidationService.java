package com.forward.hrms.employee.service;

import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.employee.entity.City;
import com.forward.hrms.employee.entity.Department;
import com.forward.hrms.employee.entity.Designation;
import com.forward.hrms.employee.entity.Grade;
import com.forward.hrms.employee.entity.Section;
import com.forward.hrms.employee.enums.EmployeeErrorCode;
import com.forward.hrms.employee.repository.CityRepository;
import com.forward.hrms.employee.repository.DepartmentRepository;
import com.forward.hrms.employee.repository.DesignationRepository;
import com.forward.hrms.employee.repository.GradeRepository;
import com.forward.hrms.employee.repository.SectionRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Validates that catalog references on an employee assignment exist and
 * are active. Centralised so the same checks are applied at onboarding,
 * during assignment changes, and from /me self-serve patches.
 */
@Service
@RequiredArgsConstructor
public class CatalogValidationService {

    private final CityRepository cities;
    private final DepartmentRepository departments;
    private final SectionRepository sections;
    private final DesignationRepository designations;
    private final GradeRepository grades;

    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public void validateAssignment(Long cityId, Long departmentId, Long sectionId,
                                   Long designationId, Long gradeId) {
        if (cityId != null) {
            City city = cities.findById(cityId)
                    .orElseThrow(() -> new NotFoundError(EmployeeErrorCode.CITY_NOT_FOUND, "city not found"));
            if (!city.isActive()) inactive("city");
        }
        Department dept = null;
        if (departmentId != null) {
            dept = departments.findById(departmentId)
                    .orElseThrow(() -> new NotFoundError(
                            EmployeeErrorCode.DEPARTMENT_NOT_FOUND, "department not found"));
            if (!dept.isActive()) inactive("department");
        }
        if (sectionId != null) {
            Section section = sections.findById(sectionId)
                    .orElseThrow(() -> new NotFoundError(
                            EmployeeErrorCode.SECTION_NOT_FOUND, "section not found"));
            if (!section.isActive()) inactive("section");
            if (dept != null && !section.getDepartment().getId().equals(dept.getId())) {
                throw new ConflictError(EmployeeErrorCode.SECTION_DEPARTMENT_MISMATCH,
                        "section does not belong to the requested department");
            }
        }
        if (designationId != null) {
            Designation d = designations.findById(designationId)
                    .orElseThrow(() -> new NotFoundError(
                            EmployeeErrorCode.DESIGNATION_NOT_FOUND, "designation not found"));
            if (!d.isActive()) inactive("designation");
        }
        if (gradeId != null) {
            Grade g = grades.findById(gradeId)
                    .orElseThrow(() -> new NotFoundError(
                            EmployeeErrorCode.GRADE_NOT_FOUND, "grade not found"));
            if (!g.isActive()) inactive("grade");
        }
    }

    private static void inactive(String kind) {
        throw new ConflictError(EmployeeErrorCode.CATALOG_INACTIVE, kind + " is inactive");
    }
}
