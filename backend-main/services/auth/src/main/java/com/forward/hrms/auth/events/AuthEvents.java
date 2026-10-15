package com.forward.hrms.auth.events;

import java.time.LocalDateTime;

import com.forward.hrms.commons.kafka.event.KafkaTopics;


public final class AuthEvents {

    public static final String SERVICE_NAME = "auth";
    public static final String TOPIC = KafkaTopics.events(SERVICE_NAME);

    public static final String USER_CREATED = "auth.user.created";
    public static final String USER_DEACTIVATED = "auth.user.deactivated";
    public static final String USER_REACTIVATED = "auth.user.reactivated";
    public static final String USER_ROLE_ASSIGNED = "auth.user.role_assigned";
    public static final String USER_ROLE_REVOKED = "auth.user.role_revoked";
    public static final String USER_PASSWORD_CHANGED = "auth.user.password_changed";
    public static final String USER_PASSWORD_RESET_BY_ADMIN = "auth.user.password_reset_by_admin";
    public static final String USER_FULL_NAME_SYNCED = "auth.user.full_name_synced";
    public static final String USER_LOCKED = "auth.user.locked";
    public static final String USER_UNLOCKED = "auth.user.unlocked";
    public static final String SESSION_CREATED = "auth.session.created";
    public static final String SESSION_REVOKED = "auth.session.revoked";
    public static final String ROLE_CREATED = "auth.role.created";

    private AuthEvents() {}

    public record UserCreated(long userId, String username, String fullName, String email, LocalDateTime createdAt) {}
    public record UserDeactivated(long userId, String reason, String by) {}
    public record UserReactivated(long userId, String by) {}
    public record UserRoleAssigned(long userId, String roleCode, String by) {}
    public record UserRoleRevoked(long userId, String roleCode, String by) {}
    public record UserPasswordChanged(long userId) {}
    public record UserPasswordResetByAdmin(long userId, String byUsername) {}
    public record UserFullNameSynced(long userId, String fullName) {}
    public record UserLocked(long userId, LocalDateTime until) {}
    public record UserUnlocked(long userId, String by) {}
    public record SessionCreated(long sessionId, long userId, String ipAddress, String userAgent) {}
    public record SessionRevoked(long sessionId, long userId, String reason, String by) {}
    public record RoleCreated(long roleId, String code, String name, String by) {}
}
