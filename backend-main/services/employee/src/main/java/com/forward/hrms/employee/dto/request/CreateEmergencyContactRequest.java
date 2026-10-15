package com.forward.hrms.employee.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "CreateEmergencyContactRequest")
public record CreateEmergencyContactRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 128)
        @NotBlank @Size(max = 128) String name,

        @Schema(description = "Relationship to employee.", maxLength = 64)
        @Size(max = 64) String relation,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 32)
        @NotBlank @Size(max = 32) String phone,

        @Schema(description = "Mark as the primary contact. Demotes any other primary on this employee.")
        boolean primary
) {}
