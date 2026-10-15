package com.forward.hrms.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Refresh-token-backed session. Active when revokedAt is null and expiresAt is in the future.")
public record SessionDto(
        @Schema(description = "Session id.", example = "412") long id,
        @Schema(description = "Best-effort device label parsed from the User-Agent at login.",
                example = "Chrome on macOS") String deviceLabel,
        @Schema(description = "Coarse device family for UI icon-picking.",
                example = "DESKTOP",
                allowableValues = {"MOBILE", "TABLET", "DESKTOP", "UNKNOWN"}) String deviceFamily,
        @Schema(description = "Browser name + version, parsed from the User-Agent. Null when unknown.")
        NameAndVersion browser,
        @Schema(description = "OS name + version, parsed from the User-Agent. Null when unknown.")
        NameAndVersion os,
        @Schema(description = "Originating IP at login.", example = "203.0.113.42") String ipAddress,
        @Schema(description = "Raw User-Agent at login.",
                example = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) ...") String userAgent,
        @Schema(description = "When the session was created.", example = "2026-04-28T08:14:22Z") LocalDateTime issuedAt,
        @Schema(description = "Last token refresh (≈ activity within the last 15 min).",
                example = "2026-04-28T09:42:00Z") LocalDateTime lastSeenAt,
        @Schema(description = "Refresh token expiry.", example = "2026-05-05T08:14:22Z") LocalDateTime expiresAt,
        @Schema(description = "True if this session backs the current request.", example = "true") boolean current,
        @Schema(description = "When the session was revoked. Null for active sessions.",
                example = "2026-04-28T10:42:11Z", nullable = true) LocalDateTime revokedAt,
        @Schema(description = "Username that revoked the session (self for user-initiated, admin for admin revoke).",
                example = "ahmed.raza", nullable = true) String revokedBy
) {

    @Schema(description = "Parsed name + optional version.")
    public record NameAndVersion(
            @Schema(example = "Chrome") String name,
            @Schema(example = "118.0.5993.117", nullable = true) String version
    ) {}
}
