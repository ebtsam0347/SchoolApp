package com.forward.hrms.employee.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Identity-only employee row keyed by userId. Returned by the by-user-ids " +
        "resolver to surface profile-picture URL + display name without requiring `employee.read`.")
public record EmployeeIdentitySummary(
        @Schema(description = "User id this employee is linked to.", example = "42", requiredMode = Schema.RequiredMode.REQUIRED)
        long userId,
        @Schema(description = "Employee id.", example = "108", requiredMode = Schema.RequiredMode.REQUIRED)
        long employeeId,
        @Schema(description = "Display name (denormalized from auth.user.fullName at onboarding, kept in sync).",
                example = "Ahmed Khan", requiredMode = Schema.RequiredMode.REQUIRED)
        String fullName,
        @Schema(description = "Pre-signed GET URL for the avatar; null if no picture set.",
                example = "https://minio.local/employee-docs/employees/108/avatar.jpg?…")
        String profilePictureUrl,
        @Schema(description = "Raw object key (callers can re-sign / cache if needed).", example = "employees/108/avatar.jpg")
        String profilePictureKey
) {}
