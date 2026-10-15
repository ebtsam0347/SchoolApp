package com.forward.hrms.employee.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Size;

@Schema(name = "UpdateQualificationRequest", description = "Patch a qualification. Null fields are left unchanged.")
public record UpdateQualificationRequest(
        @Schema(description = "Display name.", maxLength = 255) @Size(max = 255) String name,
        @Schema(description = "Level enum value.", maxLength = 32) @Size(max = 32) String level,
        @Schema(description = "Soft-deactivation flag.") Boolean active
) {}
