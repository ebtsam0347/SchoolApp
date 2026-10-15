package com.forward.hrms.bff.controller;

import com.forward.hrms.bff.dto.response.UserOverview;
import com.forward.hrms.bff.service.AdminUserOverviewService;
import com.forward.hrms.commons.web.security.RequirePermission;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin — User overview",
        description = "Composite admin views over a single user. Requires the same "
                + "permissions as the underlying admin endpoints (auth.users.read, "
                + "auth.sessions.admin, auth.roles.read, audit.read).")
@RestController
@RequestMapping("/bff/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserOverviewService service;

    @Operation(summary = "Aggregated user-detail payload",
            description = "Returns user + role catalog + active sessions + last 50 login-history "
                    + "entries + last 25 audit events authored by the user, in a single round-trip. "
                    + "Replaces the five queries the /admin/users/{id} page fans out today. "
                    + "Each underlying endpoint enforces its own @RequirePermission, so any missing "
                    + "permission surfaces as a 4xx from the failing slice. Requires `auth.users.read`.")
    @ApiResponse(responseCode = "200", description = "Composite payload assembled from auth + audit.")
    @ApiResponse(responseCode = "401", description = "Missing identity headers (gateway didn't authenticate).")
    @ApiResponse(responseCode = "403", description = "Caller lacks one of the required downstream permissions.")
    @ApiResponse(responseCode = "404", description = "User not found.")
    @ApiResponse(responseCode = "502", description = "A required downstream (auth or audit) was unreachable.")
    @GetMapping("/{id}/overview")
    @RequirePermission("auth.users.read")
    public UserOverview overview(@Parameter(description = "User id", example = "1042") @PathVariable long id) {
        return service.overview(id);
    }
}
