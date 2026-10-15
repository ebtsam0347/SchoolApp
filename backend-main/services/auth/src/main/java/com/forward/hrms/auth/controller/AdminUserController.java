package com.forward.hrms.auth.controller;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.forward.hrms.auth.client.AuditClient;
import com.forward.hrms.auth.dto.request.AttachRolesRequest;
import com.forward.hrms.auth.dto.request.CreateUserRequest;
import com.forward.hrms.auth.dto.request.DisableUserRequest;
import com.forward.hrms.auth.dto.request.IssueServiceTokenRequest;
import com.forward.hrms.auth.dto.request.PasswordResetRequest;
import com.forward.hrms.auth.dto.request.UpdateUserRequest;
import com.forward.hrms.auth.dto.response.LoginHistoryEntry;
import com.forward.hrms.auth.dto.response.LoginHistoryEntryMapper;
import com.forward.hrms.auth.dto.response.LoginHistoryPage;
import com.forward.hrms.auth.dto.response.ServiceTokenResponse;
import com.forward.hrms.auth.dto.response.SessionDto;
import com.forward.hrms.auth.dto.response.UserDto;
import com.forward.hrms.auth.dto.response.UserStatsResponse;
import com.forward.hrms.auth.enums.RevokeReason;
import com.forward.hrms.auth.enums.UserStatus;
import com.forward.hrms.auth.service.AdminUserService;
import com.forward.hrms.auth.service.SessionService;
import com.forward.hrms.commons.web.page.PageResponse;
import com.forward.hrms.commons.web.page.PageableDefaults;
import com.forward.hrms.commons.web.security.RequirePermission;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Admin — Users",
        description = "User accounts CRUD, role attach/detach, password reset, unlock, sessions. Requires `auth.users.*` / `auth.sessions.admin` permissions.")
@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService service;
    private final SessionService sessionService;
    private final AuditClient auditClient;

    @Operation(summary = "Create a user",
            description = "Issues a temp password the user must change at first login. "
                    + "When the request carries an `Idempotency-Key` header, a re-issue of "
                    + "the same payload (same username, same `employeeLinked=true`) returns "
                    + "the existing user as 200 instead of 409 — used by the onboarding saga "
                    + "to make retries safe. Requires `auth.users.write`.")
    @ApiResponse(responseCode = "201", description = "User created.")
    @ApiResponse(responseCode = "200", description = "Idempotent retry — user already existed.")
    @ApiResponse(responseCode = "409", description = "Username or email taken (no idempotency-key, or mismatched payload).")
    @PostMapping
    @RequirePermission("auth.users.write")
    public ResponseEntity<UserDto> create(
            @Valid @RequestBody CreateUserRequest req,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        boolean idempotent = idempotencyKey != null && !idempotencyKey.isBlank();
        UserDto dto = service.create(req, idempotent);
        return ResponseEntity.status(idempotent ? HttpStatus.OK : HttpStatus.CREATED).body(dto);
    }

    @Operation(summary = "Disable a user",
            description = "Idempotent. Flips status=DISABLED, revokes active sessions, "
                    + "emits `auth.user.deactivated`. Used by the termination saga to "
                    + "guarantee the disable lands even if the Kafka path is delayed, "
                    + "and by the admin UI \"disable user\" action. The optional body's "
                    + "`reason` flows into the session-revoke RevokeReason and the "
                    + "outbox event so consumers can distinguish admin disables from "
                    + "termination-driven ones; defaults to ADMIN_REVOKE when omitted. "
                    + "Requires `auth.users.write`.")
    @ApiResponse(responseCode = "200", description = "User disabled (or was already disabled).")
    @ApiResponse(responseCode = "404", description = "User not found.")
    @PostMapping("/{id}/disable")
    @RequirePermission("auth.users.write")
    public UserDto disable(@Parameter(description = "User id") @PathVariable long id,
                           @RequestBody(required = false) DisableUserRequest req) {
        RevokeReason reason = (req != null && req.reason() != null) ? req.reason() : RevokeReason.ADMIN_REVOKE;
        return service.disable(id, reason);
    }

    @Operation(summary = "Get a user by id",
            description = "Requires `auth.users.read`.")
    @ApiResponse(responseCode = "404", description = "User not found.")
    @GetMapping("/{id}")
    @RequirePermission("auth.users.read")
    public UserDto get(@Parameter(description = "User id", example = "1042") @PathVariable long id) {
        return service.get(id);
    }

    @Operation(summary = "List users (paginated)",
            description = "Standard Spring `page`, `size`, and `sort` query params apply. "
                    + "All filters are independently optional and AND-combined server-side — "
                    + "FE no longer needs to pull the whole page and filter locally. Requires `auth.users.read`.")
    @GetMapping
    @RequirePermission("auth.users.read")
    public PageResponse<UserDto> list(
            @Parameter(description = "Filter by status. Omit to return all.")
            @RequestParam(required = false) UserStatus status,
            @Parameter(description = "Case-insensitive substring against username, fullName, or email.",
                    example = "ahmed")
            @RequestParam(required = false) String q,
            @Parameter(description = "Exact role-code match (single role). Use the codes from /admin/roles "
                    + "— seeded codes are EMPLOYEE | MANAGER | HR | ADMIN (no ROLE_ prefix).",
                    example = "HR")
            @RequestParam(required = false) String role,
            @Parameter(description = "Tri-state: omit to ignore, true for ESS-linked users, false for standalone.")
            @RequestParam(required = false) Boolean employeeLinked,
            @Parameter(hidden = true) Pageable pageable) {
        return PageResponse.from(service.list(status, q, role, employeeLinked,
                PageableDefaults.apply(pageable, "username")));
    }

    @Operation(summary = "Aggregate user counts",
            description = "One round-trip for the /admin overview tile counts. Replaces the four "
                    + "paginated `size=1` reads the FE used to do just to read `totalElements`. Requires `auth.users.read`.")
    @ApiResponse(responseCode = "200", description = "Counts assembled in a single SQL pass.")
    @GetMapping("/stats")
    @RequirePermission("auth.users.read")
    public UserStatsResponse stats() {
        return service.stats();
    }

    @Operation(summary = "Patch a user", description = "Null fields are left unchanged. Requires `auth.users.write`.")
    @PatchMapping("/{id}")
    @RequirePermission("auth.users.write")
    public UserDto update(@Parameter(description = "User id") @PathVariable long id,
                          @Valid @RequestBody UpdateUserRequest req) {
        return service.update(id, req);
    }

    @Operation(summary = "Attach roles to a user", description = "Adds the supplied roles. Existing roles are kept. Requires `auth.users.write`.")
    @PostMapping("/{id}/roles")
    @RequirePermission("auth.users.write")
    public UserDto attachRoles(@Parameter(description = "User id") @PathVariable long id,
                               @Valid @RequestBody AttachRolesRequest req) {
        return service.attachRoles(id, req.roles());
    }

    @Operation(summary = "Detach a single role from a user",
            description = "Requires `auth.users.write`.")
    @DeleteMapping("/{id}/roles/{roleId}")
    @RequirePermission("auth.users.write")
    public UserDto detachRole(@Parameter(description = "User id") @PathVariable long id,
                              @Parameter(description = "Role id") @PathVariable long roleId) {
        return service.detachRole(id, roleId);
    }

    @Operation(summary = "Re-emit role grants (one-shot bootstrap)",
            description = "Re-emits `auth.user.role_assigned` for every existing "
                    + "(user, role) pair. Use this once after deploying a service "
                    + "that needs a `role_members` projection (notification's "
                    + "fan-out path for HR-targeted approvals — bug #7). The "
                    + "events going forward already cover changes; this only "
                    + "seeds the snapshot. Idempotent on the consumer side. "
                    + "Requires `auth.users.write`.")
    @ApiResponse(responseCode = "200", description = "Replay queued; response carries the count of pairs emitted.")
    @PostMapping("/replay/role-grants")
    @RequirePermission("auth.users.write")
    public java.util.Map<String, Integer> replayRoleGrants() {
        return java.util.Map.of("pairs", service.replayRoleGrants());
    }

    @Operation(summary = "Admin password reset",
            description = "Forces the user to change at next login. Active sessions remain — revoke explicitly if needed. Requires `auth.users.reset`.")
    @ApiResponse(responseCode = "204", description = "Password reset.")
    @PostMapping("/{id}/password/reset")
    @RequirePermission("auth.users.reset")
    public ResponseEntity<Void> resetPassword(@Parameter(description = "User id") @PathVariable long id,
                                              @Valid @RequestBody PasswordResetRequest req) {
        service.resetPassword(id, req.temporaryPassword());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Unlock a temporarily locked account",
            description = "Clears `lockedUntil` and resets failed-login counter. Requires `auth.users.write`.")
    @ApiResponse(responseCode = "204", description = "Account unlocked.")
    @PostMapping("/{id}/unlock")
    @RequirePermission("auth.users.write")
    public ResponseEntity<Void> unlock(@Parameter(description = "User id") @PathVariable long id) {
        service.unlock(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Mint a long-lived service-account JWT",
            description = "Issues a JWT for a service-account user (e.g. attendance-poller). " +
                    "Token is RS256-signed, identical claim shape to a login JWT plus `tokenType: \"service\"`. " +
                    "Returned once and not stored — capture it, seal it into the consumer's secret, " +
                    "and rotate by re-issuing. Resource servers accept it on signature + exp alone, so " +
                    "there is no session row, no refresh, and no revocation list. Cap is 730 days. " +
                    "Requires `auth.users.write`.")
    @ApiResponse(responseCode = "200", description = "Token issued.")
    @ApiResponse(responseCode = "404", description = "User not found.")
    @ApiResponse(responseCode = "409", description = "User is DISABLED.")
    @PostMapping("/{id}/service-tokens")
    @RequirePermission("auth.users.write")
    public ServiceTokenResponse issueServiceToken(@Parameter(description = "User id") @PathVariable long id,
                                                  @Valid @RequestBody IssueServiceTokenRequest req) {
        return service.issueServiceToken(id, req.ttlDays());
    }

    @Operation(summary = "List sessions for a user",
            description = "Returns every active session row (refresh-token side). Use admin sessions DELETE to revoke a specific one. Requires `auth.sessions.admin`.")
    @GetMapping("/{id}/sessions")
    @RequirePermission("auth.sessions.admin")
    public List<SessionDto> sessions(@Parameter(description = "User id") @PathVariable long id) {
        return sessionService.listForUserAsAdmin(id);
    }

    @Operation(summary = "Login history for a user (cursor-paginated)",
            description = "Proxies audit-service for `auth.login.success` / `auth.login.failed` / "
                    + "`user.locked` / `user.unlocked` events on this user. Pass `cursor` from the "
                    + "previous response's `nextCursor` to scroll back further than the page size. "
                    + "Requires `audit.read` — login history is operator-only and is not part of "
                    + "HR's employee timeline (V0015 doctrine). HR uses employee-service "
                    + "`/employees/{id}/history` for org-assignment changes.")
    @ApiResponse(responseCode = "200", description = "Page of login history, newest first.")
    @ApiResponse(responseCode = "502", description = "audit-service is unreachable or returned an error.")
    @GetMapping("/{id}/login-history")
    @RequirePermission("audit.read")
    public LoginHistoryPage loginHistory(
            @Parameter(description = "User id") @PathVariable long id,
            @Parameter(description = "Inclusive lower bound (ISO-8601 instant).")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @Parameter(description = "Exclusive upper bound (ISO-8601 instant).")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @Parameter(description = "Page size (default 100).")
            @RequestParam(defaultValue = "100") int limit,
            @Parameter(description = "Opaque cursor from a previous response.")
            @RequestParam(required = false) String cursor) {
        // Audit returns a page of all events for the user; we filter to login
        // actions on the way out. nextCursor is passed through verbatim — the
        // FE doesn't need to distinguish whose pagination scheme it's using.
        AuditClient.AuditPage page = auditClient.byEntity("user", String.valueOf(id), from, to, limit, cursor);
        List<LoginHistoryEntry> entries = page.content().stream()
                .filter(e -> LoginHistoryEntryMapper.isLoginAction(e.action()))
                .map(LoginHistoryEntryMapper::from)
                .toList();
        return new LoginHistoryPage(entries, page.nextCursor());
    }
}
