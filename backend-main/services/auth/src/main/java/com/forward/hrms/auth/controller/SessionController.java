package com.forward.hrms.auth.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.forward.hrms.auth.dto.response.SessionDto;
import com.forward.hrms.auth.service.SessionService;
import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.UnauthorizedError;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Sessions (self-service)",
        description = "List and revoke the caller's own active sessions. Device labels are set from the User-Agent at login and are not user-editable.")
@RestController
@RequestMapping("/auth/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService service;

    @Operation(summary = "List my active sessions",
            description = "Returns every active session for the caller, newest `lastSeenAt` first. The `current` flag marks the session backing this request.")
    @ApiResponse(responseCode = "200", description = "Active sessions for the caller.")
    @GetMapping
    public List<SessionDto> list() {
        CurrentUser cu = caller();
        return service.listOwn(cu.userId(), cu.sessionId());
    }

    @Operation(summary = "Revoke one of my sessions",
            description = "Revokes the session and deletes its refresh token. The corresponding access "
                    + "token still expires naturally (≤ 15 min). Returns the killed session so the FE "
                    + "can show \"Revoked Chrome on macOS at 10:42\" inline without re-fetching.")
    @ApiResponse(responseCode = "200", description = "Session revoked; revokedAt/revokedBy populated.")
    @ApiResponse(responseCode = "403", description = "Session does not belong to the caller.")
    @ApiResponse(responseCode = "404", description = "Session not found.")
    @DeleteMapping("/{id}")
    public SessionDto revoke(@Parameter(description = "Session id") @PathVariable long id) {
        CurrentUser cu = caller();
        return service.revokeOwn(cu.userId(), id, cu.sessionId(), cu.username());
    }

    @Operation(summary = "Revoke all my sessions",
            description = "Revokes every active session for the caller. Pass `?except=current` to keep "
                    + "this session alive (e.g. \"sign out everywhere else\"). Returns the killed sessions "
                    + "so the FE can show \"Signed out 3 sessions: Chrome on macOS, Safari on iOS, ...\" "
                    + "without re-fetching the list.")
    @ApiResponse(responseCode = "200", description = "Sessions revoked.")
    @DeleteMapping
    public BulkRevokeResult revokeAll(
            @Parameter(description = "Pass `current` to keep the calling session.")
            @RequestParam(name = "except", required = false) String except) {
        CurrentUser cu = caller();
        long keep = "current".equalsIgnoreCase(except) ? cu.sessionId() : 0L;
        List<SessionDto> killed = service.revokeAllOwnExceptCurrent(cu.userId(), keep, cu.username());
        return new BulkRevokeResult(killed);
    }

    @Schema(name = "BulkRevokeResult",
            description = "Sessions actually killed by a bulk revoke. Empty when there was nothing to revoke.")
    public record BulkRevokeResult(
            @Schema(description = "Sessions revoked, in the order they were processed (newest active first).")
            List<SessionDto> revoked
    ) {}

    private static CurrentUser caller() {
        return CurrentUserHolder.get()
                .orElseThrow(() -> new UnauthorizedError("not authenticated"));
    }
}
