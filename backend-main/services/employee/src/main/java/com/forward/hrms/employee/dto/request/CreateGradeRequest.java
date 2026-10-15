package com.forward.hrms.employee.dto.request;

import com.forward.hrms.employee.enums.GradeType;
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(name = "CreateGradeRequest")
public record CreateGradeRequest(
        @Schema(description = "Catalog code.", example = "G3",
                requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 32)
        @NotBlank @Size(max = 32) String code,

        @Schema(description = "Display name.", example = "Grade 3",
                requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 128)
        @NotBlank @Size(max = 128) String name,

        @Schema(description = "Higher = more senior. Used to sort grades.", example = "3",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Integer level,

        @Schema(description = "Optional description.")
        String description,

        @Schema(description = "Role bucket. Defaults to LABOUR when omitted; flip to MANAGEMENT or " +
                "SUPERVISOR for grades that need the management-style canteen / policy rules.",
                nullable = true)
        GradeType gradeType
) {}
