package com.forward.hrms.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "RoleDto", description = "Role with its attached permission codes.")
public record RoleDto(
        @Schema(description = "Internal role id.", example = "3") long id,
        @Schema(description = "Stable role code used in JWT claims and policy checks. Seeded codes are EMPLOYEE | MANAGER | HR | ADMIN (no ROLE_ prefix).",
                example = "HR") String code,
        @Schema(description = "Human-readable name.", example = "HR Manager") String name,
        @Schema(description = "Optional description.") String description,
        @Schema(description = "True for Flyway-seeded roles — UI should hide the Delete control on these.",
                example = "true") boolean system,
        @Schema(description = "True for roles that back a machine identity (POLLER, ATTENDANCE_SVC). "
                + "Excluded from the default catalog response — pass `includeServiceAccounts=true` "
                + "on /admin/roles to see them.",
                example = "false") boolean serviceAccount,
        @Schema(description = "Permission codes the role grants (sorted, distinct).",
                example = "[\"auth.users.read\",\"auth.users.write\"]") List<String> permissions
) {}
