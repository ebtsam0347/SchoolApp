package com.forward.hrms.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "One login-related audit event for a user.")
public record LoginHistoryEntry(
        @Schema(description = "When the event occurred.", example = "2026-04-28T08:14:22Z") LocalDateTime occurredAt,
        @Schema(description = "Audit action.",
                example = "login.success",
                allowableValues = {"login.success", "login.failed", "user.locked", "user.unlocked"})
        String action,
        @Schema(description = "True if the login attempt succeeded.", example = "true") boolean success,
        @Schema(description = "Originating IP, if recorded.", example = "203.0.113.42") String ipAddress,
        @Schema(description = "User-Agent header, if recorded.",
                example = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) ...") String userAgent,
        @Schema(description = "Failure reason for failed/locked entries (BAD_PASSWORD, USER_DISABLED, USER_LOCKED).",
                example = "BAD_PASSWORD") String reason
) {}
