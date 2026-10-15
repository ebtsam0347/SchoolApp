package com.forward.hrms.bff.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Composed user-identity row the FE uses for chat / mention chips. " +
        "Joins auth.user.username + auth.user.fullName with employee.profilePictureUrl " +
        "(via employee.user_id). Avatar is null for admin-only users that have no employee row.")
public record UserIdentitySummary(
        @Schema(description = "User id.", example = "42", requiredMode = Schema.RequiredMode.REQUIRED)
        long userId,
        @Schema(description = "Login handle. Stable. Used as the @mention token.",
                example = "ahmed.khan", requiredMode = Schema.RequiredMode.REQUIRED)
        String username,
        @Schema(description = "Display name.", example = "Ahmed Khan", requiredMode = Schema.RequiredMode.REQUIRED)
        String fullName,
        @Schema(description = "Pre-signed avatar URL; null when no employee row or no picture set.",
                example = "https://minio.local/employee-docs/employees/108/avatar.jpg?…")
        String profilePictureUrl,
        @Schema(description = "Account status from auth-service. UI may grey out non-ACTIVE rows.",
                example = "ACTIVE", requiredMode = Schema.RequiredMode.REQUIRED)
        String status
) {}
