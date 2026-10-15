package com.forward.hrms.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;

@Schema(name = "PasswordChangeRequest", description = "Self-service password change. Both fields required.")
public record PasswordChangeRequest(
        @Schema(description = "Current password.", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank String oldPassword,

        @Schema(description = "New password. Must satisfy server-side password policy.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank String newPassword
) {}
