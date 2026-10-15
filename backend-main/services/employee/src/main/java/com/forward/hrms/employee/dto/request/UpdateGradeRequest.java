package com.forward.hrms.employee.dto.request;

import com.forward.hrms.employee.enums.GradeType;
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Size;

@Schema(name = "UpdateGradeRequest", description = "Patch a grade. Null fields are left unchanged.")
public record UpdateGradeRequest(
        @Schema(description = "Display name.", maxLength = 128) @Size(max = 128) String name,
        @Schema(description = "Higher = more senior.") Integer level,
        @Schema(description = "Optional description.") String description,
        @Schema(description = "Soft-deactivation flag.") Boolean active,
        @Schema(description = "Role bucket. MANAGEMENT / SUPERVISOR / LABOUR.") GradeType gradeType
) {}
