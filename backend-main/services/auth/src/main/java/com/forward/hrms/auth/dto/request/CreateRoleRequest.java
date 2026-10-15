package com.forward.hrms.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

@Schema(name = "CreateRoleRequest", description = "Admin payload to create a custom role.")
public record CreateRoleRequest(
        @Schema(description = "Stable role code. Conventionally upper-snake-case, no ROLE_ prefix "
                + "(matches the seeded EMPLOYEE | MANAGER | HR | ADMIN). Used in JWT claims and policy checks.",
                example = "PAYROLL_READONLY", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 64)
        @Pattern(regexp = "^[A-Z][A-Z0-9_]*$",
                message = "must be UPPER_SNAKE_CASE (A-Z, 0-9, _; starting with a letter)")
        String code,

        @Schema(description = "Human-readable name shown in the admin UI.",
                example = "Payroll Read-only", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 128) String name,

        @Schema(description = "Optional 1-line description.", example = "Audit consultant — payroll read-only.")
        @Size(max = 1000) String description,

        @Schema(description = "Permission codes to attach immediately. Must already exist in /admin/permissions.",
                example = "[\"audit.read\",\"employee.read\"]",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty Set<String> permissions
) {}
