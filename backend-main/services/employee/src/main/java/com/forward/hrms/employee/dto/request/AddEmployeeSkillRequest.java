package com.forward.hrms.employee.dto.request;

import com.forward.hrms.employee.enums.Proficiency;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

@Schema(name = "AddEmployeeSkillRequest")
public record AddEmployeeSkillRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long skillId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Proficiency proficiency,
        @Schema(nullable = true) BigDecimal years,
        @Schema(nullable = true) String notes
) {}
