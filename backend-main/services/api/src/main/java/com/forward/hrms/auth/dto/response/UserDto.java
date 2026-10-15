package com.forward.hrms.auth.dto.response;

import com.forward.hrms.auth.enums.UserStatus;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(name = "UserDto", description = "User account view — admin lists, get-by-id, and post-mutation responses.")
public record UserDto(
        @Schema(description = "Internal user id.", example = "1042") long id,
        @Schema(description = "Login name.", example = "ahmed.raza") String username,
        @Schema(description = "Display name.", example = "Ahmed Raza") String fullName,
        @Schema(description = "Contact email (nullable).") String email,
        @Schema(description = "Lifecycle status — drives whether the user can log in.") UserStatus status,
        @Schema(description = "True until the user changes their initial / reset password.") boolean mustChangePw,
        @Schema(description = "True when linked to an employee record (ESS).") boolean employeeLinked,
        @Schema(description = "Role codes assigned (sorted, distinct; no ROLE_ prefix).", example = "[\"HR\"]") List<String> roles,
        @Schema(description = "Last successful login (UTC).") LocalDateTime lastLoginAt,
        @Schema(description = "If non-null, account is locked until this UTC instant due to failed login attempts.")
        LocalDateTime lockedUntil,
        @Schema(description = "Created at (UTC).") LocalDateTime createdAt,
        @Schema(description = "Updated at (UTC).") LocalDateTime updatedAt
) {}
