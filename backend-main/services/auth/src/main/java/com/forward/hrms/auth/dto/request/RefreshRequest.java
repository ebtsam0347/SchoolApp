package com.forward.hrms.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;

@Schema(name = "RefreshRequest", description = "Opaque refresh token returned at login or last refresh.")
public record RefreshRequest(
        @Schema(description = "Opaque refresh token. Treat as a credential — do not log.",
                example = "eyJhbGciOi...", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank String refreshToken
) {}
