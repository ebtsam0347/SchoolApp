package com.forward.hrms.employee.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Size;

@Schema(name = "UpdateDepartmentRequest", description = "Patch a department. Null fields are left unchanged.")
public record UpdateDepartmentRequest(
        @Schema(description = "Display name.", maxLength = 128) @Size(max = 128) String name,

        @Schema(description = "Department head's employee id. Pass `0` to clear.")
        Long headId,

        @Schema(description = "Soft-deactivation flag.") Boolean active,

        @Schema(description = "Marks the department as a production unit (factory floor).") Boolean isProduction
) {}
