package com.forward.hrms.audit.controller;

import tools.jackson.databind.ObjectMapper;

import com.forward.hrms.audit.dto.response.CursorPage;
import com.forward.hrms.audit.dto.response.SignInEvent;
import com.forward.hrms.audit.dto.response.TimelinePage;
import com.forward.hrms.audit.entity.AuditEvent;
import com.forward.hrms.audit.repository.AuditEventRepository;
import com.forward.hrms.audit.service.EntityHistoryCursor;
import com.forward.hrms.audit.service.TimelinePolicy;
import com.forward.hrms.audit.service.TimelineService;
import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.UnauthorizedError;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@Tag(name = "Timeline",
        description = "User-facing timeline per entity. Returns only events flagged `user_visible = true`. "
                + "HR/ADMIN see everything; a regular user can read their own user/employee timeline.")
@RestController
@RequestMapping("/timeline")
public class TimelineController {

    private final TimelineService service;
    private final TimelinePolicy policy;
    private final AuditEventRepository repo;
    private final ObjectMapper json;

    public TimelineController(TimelineService service, TimelinePolicy policy,
                              AuditEventRepository repo,
                              ObjectMapper json) {
        this.service = service;
        this.policy = policy;
        this.repo = repo;
        this.json = json;
    }

    @Operation(summary = "Read an entity timeline (user-visible events only)",
            description = "Cursor pagination via the opaque `cursor` returned in the previous page's `nextCursor`.")
    @ApiResponse(responseCode = "200", description = "Timeline page (newest first).")
    @ApiResponse(responseCode = "403", description = "Caller may not read this entity's timeline.")
    @GetMapping("/{entityType}/{entityId}")
    public TimelinePage timeline(
            @Parameter(description = "Entity type, e.g. `employee` or `user`.", example = "user")
            @PathVariable String entityType,
            @Parameter(description = "Entity id (string for flexibility).", example = "1042")
            @PathVariable String entityId,
            @Parameter(description = "Inclusive lower bound (ISO-8601 instant).")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @Parameter(description = "Exclusive upper bound (ISO-8601 instant).")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @Parameter(description = "Page size (default 50, max 200).")
            @RequestParam(required = false) Integer limit,
            @Parameter(description = "Opaque cursor from a previous response.")
            @RequestParam(required = false) String cursor) {
        policy.check(entityType, entityId);
        return service.timeline(entityType, entityId, from, to, limit, cursor);
    }

    /**
     * Self-service sign-in feed. Always scoped to the authenticated
     * caller's own user id — there is no path/query id to spoof. Bypasses
     * the {@code audit.read} gate (a regular user cannot read the audit
     * log generally) and the {@code userVisible} filter (failed-login
     * events are flagged user_visible=false for the public timeline; on
     * the user's Security page they're the high-value rows).
     */
    @Operation(summary = "List my sign-in events (cursor-paginated)",
            description = "Caller's login.success / login.failed / user.locked / user.unlocked "
                    + "events, newest first. Pass `cursor` from the previous response's `nextCursor` "
                    + "to scroll back. Always scoped to the calling user; no userId param.")
    @ApiResponse(responseCode = "200", description = "Page of sign-in events for the caller.")
    @ApiResponse(responseCode = "401", description = "Authentication required.")
    @GetMapping("/self/sign-in-events")
    @PreAuthorize("isAuthenticated()")
    public CursorPage<SignInEvent> mySignInEvents(
            @Parameter(description = "Inclusive lower bound (ISO-8601 instant).")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @Parameter(description = "Exclusive upper bound (ISO-8601 instant).")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @Parameter(description = "Page size (default 100, max 500).")
            @RequestParam(defaultValue = "100") int limit,
            @Parameter(description = "Opaque cursor from a previous response.")
            @RequestParam(required = false) String cursor) {
        long userId = caller().userId();
        int size = clamp(limit);
        EntityHistoryCursor c = EntityHistoryCursor.parse(cursor);
        // Over-fetch by some factor so the post-filter for sign-in actions
        // still has enough rows to fill `size`. Sign-in events are the
        // common case for the user entity, so 2x is generous.
        int rawSize = size * 2 + 1;
        List<AuditEvent> rows = repo.findByEntityCursor("user", String.valueOf(userId), from, to,
                c == null ? null : c.ts(),
                c == null ? null : c.id(),
                Pageable.ofSize(rawSize));
        List<AuditEvent> filtered = rows.stream()
                .filter(e -> SignInEvent.isSignInAction(e.getAction()))
                .toList();
        boolean hasMore = filtered.size() > size;
        List<AuditEvent> page = hasMore ? filtered.subList(0, size) : filtered;
        String next = hasMore
                ? new EntityHistoryCursor(page.get(page.size() - 1).getOccurredAt(),
                        page.get(page.size() - 1).getId()).encode()
                : null;
        return new CursorPage<>(page.stream().map(e -> SignInEvent.of(e, json)).toList(), next);
    }

    private static CurrentUser caller() {
        return CurrentUserHolder.get()
                .orElseThrow(() -> new UnauthorizedError("Authentication required."));
    }

    /** Mirrors {@code AuditController.clamp}; kept private to avoid leaking constants between controllers. */
    private static int clamp(int requested) {
        if (requested <= 0) return 100;
        return Math.min(requested, 500);
    }
}
