package com.forward.hrms.employee.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "GrantEssRequest", description = "Grant ESS to an existing employee.")
public record GrantEssRequest(
        @Schema(description = "Login name. Falls back to a slug of the employee's full name when omitted.")
        @Size(max = 64) String username,

        @Schema(description = "Email for the ESS user. Falls back to workEmail.")
        @Email String email,

        @Schema(description = "Temporary password — user is forced to change at first login.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank String temporaryPassword
) {}
