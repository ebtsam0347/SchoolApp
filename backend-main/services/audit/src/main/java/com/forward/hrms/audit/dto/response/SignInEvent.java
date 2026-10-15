package com.forward.hrms.audit.dto.response;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import com.forward.hrms.audit.entity.AuditEvent;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

/**
 * Self-service sign-in feed row. Same shape as auth-service's
 * {@code LoginHistoryEntry} but built from the audit row directly.
 *
 * <p>Surfaces both successes and {@code userVisible = false} failures
 * (which are the high-value rows for the user's Security page —
 * "we noticed 3 failed sign-ins from Lahore last night, was that you?").
 */
@Schema(description = "One sign-in event for the caller's own user record.")
public record SignInEvent(
        @Schema(description = "When the event occurred.", example = "2026-04-28T08:14:22Z") LocalDateTime occurredAt,
        @Schema(description = "Audit action.",
                example = "login.success",
                allowableValues = {"login.success", "login.failed", "user.locked", "user.unlocked"})
        String action,
        @Schema(description = "True if the login attempt succeeded.", example = "false") boolean success,
        @Schema(description = "Originating IP, if recorded.", example = "203.0.113.42") String ipAddress,
        @Schema(description = "User-Agent header, if recorded.",
                example = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) ...") String userAgent,
        @Schema(description = "Failure reason for failed/locked entries (BAD_PASSWORD, USER_DISABLED, USER_LOCKED).",
                example = "BAD_PASSWORD") String reason
) {

    public static final Set<String> SIGN_IN_ACTIONS =
            Set.of("login.success", "login.failed", "user.locked", "user.unlocked");

    public static boolean isSignInAction(String action) {
        return action != null && SIGN_IN_ACTIONS.contains(action);
    }

    public static SignInEvent of(AuditEvent e, ObjectMapper json) {
        Map<String, Object> meta = parseToMap(json, e.getMetadata());
        Map<String, Object> after = parseToMap(json, e.getAfterJson());
        String ip = e.getIpAddress() != null ? e.getIpAddress() : asString(meta.get("ip"));
        return new SignInEvent(
                e.getOccurredAt(),
                e.getAction(),
                "login.success".equals(e.getAction()),
                ip,
                asString(meta.get("userAgent")),
                asString(after.get("reason"))
        );
    }

    private static Map<String, Object> parseToMap(ObjectMapper json, String raw) {
        if (raw == null || raw.isBlank()) return Map.of();
        try {
            JsonNode node = json.readTree(raw);
            if (!node.isObject()) return Map.of();
            return json.convertValue(node, Map.class);
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private static String asString(Object v) {
        return v == null ? null : v.toString();
    }
}
