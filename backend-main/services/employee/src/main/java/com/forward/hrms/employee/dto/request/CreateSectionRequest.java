package com.forward.hrms.employee.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(name = "CreateSectionRequest")
public record CreateSectionRequest(
        @Schema(description = "Catalog code.", example = "QC_FB",
                requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 32)
        @NotBlank @Size(max = 32) String code,

        @Schema(description = "Display name.", example = "Quality Control - Footballs",
                requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 128)
        @NotBlank @Size(max = 128) String name,

        @Schema(description = "Parent department id.", example = "3",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Long departmentId,

        @Schema(description = "Optional initial head (employee id).", example = "42")
        Long headId
) {}
