package com.forward.hrms.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "PermissionDto", description = "A single permission row from the catalog.")
public record PermissionDto(
        @Schema(description = "Internal permission id.", example = "12") long id,
        @Schema(description = "Stable permission code referenced from @RequirePermission and policy checks.",
                example = "auth.users.write") String code,
        @Schema(description = "Human-readable name.", example = "Manage users") String name,
        @Schema(description = "UI grouping for the permission catalog (server-decided so non-engineers see "
                + "\"Identity\" instead of \"auth.users.*\").",
                example = "Identity") String group,
        @Schema(description = "1-line plain-English explanation of what the permission allows.",
                example = "Create, update, lock, and unlock user accounts.") String description
) {}
