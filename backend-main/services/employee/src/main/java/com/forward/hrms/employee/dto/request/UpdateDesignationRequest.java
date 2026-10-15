package com.forward.hrms.employee.dto.request;

import com.forward.hrms.employee.enums.DesignationCategory;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Size;

@Schema(name = "UpdateDesignationRequest", description = "Patch a designation. Null fields are left unchanged.")
public record UpdateDesignationRequest(
        @Schema(description = "Display title.", maxLength = 128) @Size(max = 128) String title,
        @Schema(description = "DIRECT vs INDIRECT.", nullable = true) DesignationCategory category,
        @Schema(description = "Soft-deactivation flag.") Boolean active
) {}
