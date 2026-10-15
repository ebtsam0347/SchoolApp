package com.forward.hrms.employee.dto.request;

import com.forward.hrms.employee.enums.DisabilitySeverity;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Size;

@Schema(name = "UpdateEmployeeDisabilityRequest",
        description = "Patch the description / severity / document key. The disability id itself is " +
                "immutable — to change it, delete the row and add a new one.")
public record UpdateEmployeeDisabilityRequest(
        @Schema(nullable = true) @Size(max = 255) String description,
        @Schema(nullable = true) DisabilitySeverity severity,
        @Schema(nullable = true) @Size(max = 512) String documentKey
) {}
