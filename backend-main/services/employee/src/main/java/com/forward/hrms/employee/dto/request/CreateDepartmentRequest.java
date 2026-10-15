package com.forward.hrms.employee.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "CreateDepartmentRequest")
public record CreateDepartmentRequest(
        @Schema(description = "Catalog code.", example = "PROD",
                requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 32)
        @NotBlank @Size(max = 32) String code,

        @Schema(description = "Display name.", example = "Production",
                requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 128)
        @NotBlank @Size(max = 128) String name,

        @Schema(description = "Optional initial head (employee id).", example = "42")
        Long headId,

        @Schema(description = "Marks the department as a production unit (factory floor). Defaults to false.",
                example = "false")
        Boolean isProduction
) {}
