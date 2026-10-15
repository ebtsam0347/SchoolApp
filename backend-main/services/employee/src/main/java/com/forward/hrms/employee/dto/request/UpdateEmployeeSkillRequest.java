package com.forward.hrms.employee.dto.request;

import com.forward.hrms.employee.enums.Proficiency;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(name = "UpdateEmployeeSkillRequest", description = "Patch. Null fields are left unchanged.")
public record UpdateEmployeeSkillRequest(
        Proficiency proficiency,
        BigDecimal years,
        String notes
) {}
