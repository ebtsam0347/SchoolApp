package com.forward.hrms.employee.dto.response;

import com.forward.hrms.employee.entity.Employee;
import com.forward.hrms.employee.entity.EmployeeQualification;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "QualificationEmployeeDto",
        description = "Reverse-lookup row: an employee who holds a given qualification.")
public record QualificationEmployeeDto(
        long employeeQualificationId,
        long employeeId,
        @Schema(example = "FS-2026-0042") String employeeCode,
        String fullName,
        String institution,
        @Schema(nullable = true) Integer yearOfCompletion
) {
    public static QualificationEmployeeDto of(EmployeeQualification eq, Employee e) {
        return new QualificationEmployeeDto(
                eq.getId(), eq.getEmployeeId(),
                e == null ? null : e.getEmployeeCode(),
                e == null ? null : e.getFullName(),
                eq.getInstitution(), eq.getYearOfCompletion());
    }
}
