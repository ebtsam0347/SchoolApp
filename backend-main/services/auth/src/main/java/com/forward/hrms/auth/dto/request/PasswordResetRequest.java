package com.forward.hrms.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;

@Schema(name = "PasswordResetRequest",
        description = "Admin password reset. The user is forced to change on next login.")
public record PasswordResetRequest(
        @Schema(description = "Temporary password to set on the account.",
                example = "Reset!2026", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank String temporaryPassword
) {}
