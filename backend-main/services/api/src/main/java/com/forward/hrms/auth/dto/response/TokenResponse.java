package com.forward.hrms.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(name = "TokenResponse",
        description = "Access + refresh token pair. Send the access token as `Authorization: Bearer <accessToken>`.")
public record TokenResponse(
        @Schema(description = "Signed JWT (RS256). Place in the Authorization header for protected calls.")
        String accessToken,

        @Schema(description = "UTC instant the access token expires. Refresh before this to avoid 401.",
                example = "2026-04-28T12:15:00Z")
        LocalDateTime accessTokenExpiresAt,

        @Schema(description = "Opaque refresh token bound to the active session. Send to /auth/refresh to mint new tokens.")
        String refreshToken,

        @Schema(description = "UTC instant the refresh token expires. After this the user must log in again.",
                example = "2026-05-05T12:00:00Z")
        LocalDateTime refreshTokenExpiresAt,

        @Schema(description = "Server-side session id. Used by admin endpoints to revoke the session.",
                example = "1042")
        long sessionId,

        @Schema(description = "True when HR provisioned a temporary password — UI must force a change before any other action.")
        boolean mustChangePassword
) {}
