package com.forward.hrms.employee.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Size;

@Schema(name = "UpdateSkillRequest", description = "Patch a skill. Null fields are left unchanged.")
public record UpdateSkillRequest(
        @Schema(description = "Display name.", maxLength = 128) @Size(max = 128) String name,
        @Schema(description = "Loose category.", maxLength = 32) @Size(max = 32) String category,
        @Schema(description = "Soft-deactivation flag.") Boolean active
) {}
