package com.forward.hrms.auth.dto.response;

import com.forward.hrms.auth.enums.UserStatus;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Minimal user-identity row returned by the by-ids resolver. " +
        "Intentionally narrow: only the fields a chat / mention / directory chip needs.")
public record UserIdentitySummary(
        @Schema(description = "User id.", example = "42", requiredMode = Schema.RequiredMode.REQUIRED)
        long id,
        @Schema(description = "Login handle. Stable. Used for @mentions.", example = "ahmed.khan", requiredMode = Schema.RequiredMode.REQUIRED)
        String username,
        @Schema(description = "Display name.", example = "Ahmed Khan", requiredMode = Schema.RequiredMode.REQUIRED)
        String fullName,
        @Schema(description = "Account status. UI may grey out non-ACTIVE rows.", example = "ACTIVE", requiredMode = Schema.RequiredMode.REQUIRED)
        UserStatus status
) {}
