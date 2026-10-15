package com.forward.hrms.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Size;

import java.util.Set;

@Schema(name = "UpdateRoleRequest",
        description = "Patch a role. Null fields are left unchanged. The `code` is immutable — change a role's "
                + "code by creating a new role and re-assigning users (the code is part of the JWT contract).")
public record UpdateRoleRequest(
        @Schema(description = "New display name.")
        @Size(max = 128) String name,

        @Schema(description = "New description.")
        @Size(max = 1000) String description,

        @Schema(description = "Replace the role's permissions with this set. Send the full target set, not a diff. "
                + "Pass null to leave permissions untouched.",
                example = "[\"audit.read\",\"employee.read\"]")
        Set<String> permissions
) {}
