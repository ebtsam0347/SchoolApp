package com.forward.hrms.employee.dto.response;

import com.forward.hrms.employee.entity.Employee;
import com.forward.hrms.employee.entity.EmployeeSkill;
import com.forward.hrms.employee.enums.Proficiency;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(name = "SkillEmployeeDto",
        description = "Reverse-lookup row: an employee who holds a given skill.")
public record SkillEmployeeDto(
        long employeeSkillId,
        long employeeId,
        @Schema(description = "Denormalized employee code.", example = "FS-2026-0042") String employeeCode,
        @Schema(description = "Denormalized employee name.") String fullName,
        Proficiency proficiency,
        @Schema(nullable = true) BigDecimal years
) {
    public static SkillEmployeeDto of(EmployeeSkill es, Employee e) {
        return new SkillEmployeeDto(
                es.getId(), es.getEmployeeId(),
                e == null ? null : e.getEmployeeCode(),
                e == null ? null : e.getFullName(),
                es.getProficiency(), es.getYears());
    }
}
