package com.forward.hrms.auth.dto.request;

import com.forward.hrms.auth.enums.RevokeReason;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "DisableUserRequest",
        description = "Optional body for POST /admin/users/{id}/disable. The reason flows into the session-revoke "
                + "RevokeReason and the auth.user.deactivated outbox event so downstream consumers can "
                + "distinguish admin-initiated disables from termination-driven ones.")
public record DisableUserRequest(
        @Schema(description = "Why the user is being disabled. Defaults to ADMIN_REVOKE when omitted, which "
                + "is the right value for admin UI \"disable user\" actions and for the onboarding saga's "
                + "compensation path. The termination saga passes EMPLOYEE_TERMINATED.",
                example = "ADMIN_REVOKE")
        RevokeReason reason
) {}
