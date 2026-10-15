package com.forward.hrms.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;

@Schema(name = "LoginRequest", description = "Credentials for password-based login.")
public record LoginRequest(
        @Schema(description = "Username (case-sensitive). Same value used at user creation time.",
                example = "admin", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank String username,

        @Schema(description = "Plain-text password. Sent over TLS in non-dev environments.",
                example = "ChangeMe!2026", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank String password
) {}
