package com.forward.hrms.auth.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.forward.hrms.auth.dto.response.SessionDto;
import com.forward.hrms.auth.service.SessionService;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.web.security.RequirePermission;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Admin — Sessions",
        description = "Force-revoke a user's active session. Requires `auth.sessions.admin`.")
@RestController
@RequestMapping("/admin/sessions")
@RequiredArgsConstructor
public class AdminSessionController {

    private final SessionService sessions;

    @Operation(summary = "Revoke a session by id",
            description = "Marks the session row revoked and invalidates the refresh token. Idempotent. "
                    + "Returns the killed session so the admin UI can show \"Revoked Chrome on macOS at 10:42\" "
                    + "inline without re-fetching the user's session list. Requires `auth.sessions.admin`.")
    @ApiResponse(responseCode = "200", description = "Session revoked; revokedAt/revokedBy populated.")
    @ApiResponse(responseCode = "404", description = "Session not found.")
    @DeleteMapping("/{id}")
    @RequirePermission("auth.sessions.admin")
    public SessionDto revoke(@Parameter(description = "Session id") @PathVariable long id) {
        return sessions.revokeAsAdmin(id, CurrentUserHolder.usernameOrSystem());
    }
}
