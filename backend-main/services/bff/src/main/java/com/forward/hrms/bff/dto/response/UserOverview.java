package com.forward.hrms.bff.dto.response;

import com.forward.hrms.auth.dto.response.LoginHistoryEntry;
import com.forward.hrms.auth.dto.response.SessionDto;
import com.forward.hrms.auth.dto.response.UserDto;
import com.forward.hrms.audit.dto.response.AuditEventDto;
import com.forward.hrms.auth.dto.response.RoleDto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "UserOverview",
        description = "Composite payload for the admin /admin/users/{id} console: user, "
                + "role catalog, active sessions, recent login history, recent admin-visible "
                + "audit events. Replaces the five queries the page fans out today.")
public record UserOverview(
        @Schema(description = "Full user record (from /admin/users/{id}).") UserDto user,

        @Schema(description = "Full role catalog so the available-roles panel can render "
                + "without a separate fetch (from /admin/roles).") List<RoleDto> roles,

        @Schema(description = "Active sessions for this user (from /admin/users/{id}/sessions).")
        List<SessionDto> sessions,

        @Schema(description = "Last 50 login-related events for this user "
                + "(from /admin/users/{id}/login-history).") List<LoginHistoryEntry> loginHistory,

        @Schema(description = "Last 25 audit events authored by this user, across services "
                + "(from /audit/by-actor/{id}).") List<AuditEventDto> recentActions
) {}
