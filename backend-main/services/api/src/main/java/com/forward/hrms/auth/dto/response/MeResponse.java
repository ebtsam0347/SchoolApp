package com.forward.hrms.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(name = "MeResponse", description = "Current user identity, roles, and effective permissions.")
public record MeResponse(
        @Schema(description = "Internal user id.", example = "1") long userId,
        @Schema(description = "Username used at login.", example = "admin") String username,
        @Schema(description = "Display name.", example = "System Administrator") String fullName,
        @Schema(description = "Contact email (nullable for system users).", example = "admin@forwardsports.com") String email,
        @Schema(description = "True when the user is linked to an employee record (ESS access).") boolean employeeLinked,
        @Schema(description = "True if the user must change their password before doing anything else.") boolean mustChangePassword,
        @Schema(description = "Role codes assigned to the user (no ROLE_ prefix; matches the Flyway seed).",
                example = "[\"HR\",\"ADMIN\"]") List<String> roles,
        @Schema(description = "Flattened, distinct permission codes from all roles.",
                example = "[\"auth.users.read\",\"auth.users.write\"]") List<String> permissions,
        @Schema(description = "Last successful login (UTC). Null on first login.") LocalDateTime lastLoginAt
) {}
