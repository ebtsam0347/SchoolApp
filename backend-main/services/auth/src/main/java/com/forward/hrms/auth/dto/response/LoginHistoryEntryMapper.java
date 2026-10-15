package com.forward.hrms.auth.dto.response;

import com.forward.hrms.auth.client.AuditClient;

import java.util.Map;
import java.util.Set;

public final class LoginHistoryEntryMapper {

    private static final Set<String> LOGIN_ACTIONS =
            Set.of("login.success", "login.failed", "user.locked", "user.unlocked");

    private LoginHistoryEntryMapper() {}

    public static boolean isLoginAction(String action) {
        return action != null && LOGIN_ACTIONS.contains(action);
    }

    public static LoginHistoryEntry from(AuditClient.AuditEvent e) {
        Map<String, Object> meta = e.metadata() == null ? Map.of() : e.metadata();
        Map<String, Object> after = e.after() == null ? Map.of() : e.after();
        return new LoginHistoryEntry(
                e.occurredAt(),
                e.action(),
                "login.success".equals(e.action()),
                e.ipAddress() != null ? e.ipAddress() : asString(meta.get("ip")),
                asString(meta.get("userAgent")),
                asString(after.get("reason"))
        );
    }

    private static String asString(Object v) {
        return v == null ? null : v.toString();
    }
}
