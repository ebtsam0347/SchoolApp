package com.forward.hrms.commons.core.context;

import java.util.Set;

public record CurrentUser(long userId, String username, String fullName, long sessionId,
                          Set<String> roles, Set<String> permissions) {

    public static final String SYSTEM_USERNAME = "system";

    public CurrentUser {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }

    /** No fullName, no session — system actors / Kafka consumers. */
    public CurrentUser(long userId, String username, Set<String> roles, Set<String> permissions) {
        this(userId, username, null, 0L, roles, permissions);
    }

    /** Back-compat overload — pre-fullName call sites that already track sessionId. */
    public CurrentUser(long userId, String username, long sessionId,
                       Set<String> roles, Set<String> permissions) {
        this(userId, username, null, sessionId, roles, permissions);
    }

    public boolean hasRole(String role) { return roles.contains(role); }
    public boolean hasPermission(String permission) { return permissions.contains(permission); }
}
