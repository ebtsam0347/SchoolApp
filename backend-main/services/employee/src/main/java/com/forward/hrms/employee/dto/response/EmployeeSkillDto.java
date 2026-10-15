package com.forward.hrms.employee.dto.response;

import com.forward.hrms.employee.entity.EmployeeSkill;
import com.forward.hrms.employee.entity.Skill;
import com.forward.hrms.employee.enums.Proficiency;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(name = "EmployeeSkillDto", description = "Per-employee proficiency on a catalog skill.")
public record EmployeeSkillDto(
        long id,
        long employeeId,
        long skillId,
        @Schema(description = "Catalog code, denormalized.", example = "TECH_JAVA") String skillCode,
        @Schema(description = "Catalog name, denormalized.", example = "Java") String skillName,
        Proficiency proficiency,
        @Schema(nullable = true) BigDecimal years,
        @Schema(nullable = true) String notes
) {
    public static EmployeeSkillDto of(EmployeeSkill es, Skill s) {
        return new EmployeeSkillDto(
                es.getId(), es.getEmployeeId(), es.getSkillId(),
                s == null ? null : s.getCode(),
                s == null ? null : s.getName(),
                es.getProficiency(), es.getYears(), es.getNotes());
    }
}
