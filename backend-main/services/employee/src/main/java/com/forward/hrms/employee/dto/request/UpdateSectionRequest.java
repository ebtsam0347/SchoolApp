package com.forward.hrms.employee.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Size;

@Schema(name = "UpdateSectionRequest", description = "Patch a section. Null fields are left unchanged.")
public record UpdateSectionRequest(
        @Schema(description = "Display name.", maxLength = 128) @Size(max = 128) String name,

        @Schema(description = "Move to a different department.") Long departmentId,

        @Schema(description = "Section head's employee id. Pass `0` to clear.")
        Long headId,

        @Schema(description = "Soft-deactivation flag.") Boolean active
) {}
