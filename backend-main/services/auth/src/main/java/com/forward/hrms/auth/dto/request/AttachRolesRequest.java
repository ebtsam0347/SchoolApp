package com.forward.hrms.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

@Schema(name = "AttachRolesRequest", description = "Roles to attach to a user. At least one is required.")
public record AttachRolesRequest(
        @Schema(description = "Role codes to attach (no ROLE_ prefix; matches the seeded EMPLOYEE | MANAGER | HR | ADMIN).",
                example = "[\"HR\"]",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty Set<String> roles
) {}
