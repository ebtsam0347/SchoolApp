package com.forward.hrms.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;

@Schema(name = "CreateUserRequest", description = "Admin payload to create a new user account.")
public record CreateUserRequest(
        @Schema(description = "Unique username (case-sensitive). Used at login.",
                example = "ahmed.raza", maxLength = 64,
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 64) String username,

        @Schema(description = "Display name shown in UI.", example = "Ahmed Raza",
                maxLength = 128, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 128) String fullName,

        @Schema(description = "Optional contact email.", example = "ahmed.raza@forwardsports.com", maxLength = 255)
        @Email @Size(max = 255) String email,

        @Schema(description = "Temporary password — user is forced to change on first login.",
                example = "Welcome!2026", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank String temporaryPassword,

        @Schema(description = "True when the account is linked to an employee record (ESS). " +
                "Reverse linkage (auth → employee) is not stored on `users`; employee-service " +
                "owns the relationship via `employees.user_id`.")
        boolean employeeLinked,

        @Schema(description = "Role codes to attach immediately (no ROLE_ prefix; matches the seeded EMPLOYEE | MANAGER | HR | ADMIN).",
                example = "[\"HR\"]")
        Set<String> roles
) {}
