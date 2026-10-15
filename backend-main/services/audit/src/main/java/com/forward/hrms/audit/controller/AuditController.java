package com.forward.hrms.audit.controller;

import tools.jackson.databind.ObjectMapper;

import com.forward.hrms.audit.dto.response.AuditEventDto;
import com.forward.hrms.audit.dto.response.CursorPage;
import com.forward.hrms.audit.dto.response.FacetsResponse;
import com.forward.hrms.audit.entity.AuditEvent;
import com.forward.hrms.audit.enums.AuditErrorCode;
import com.forward.hrms.audit.repository.AuditEventRepository;
import com.forward.hrms.audit.repository.AuditEventSpecifications;
import com.forward.hrms.audit.service.EntityHistoryCursor;
import com.forward.hrms.audit.service.FacetsService;
import com.forward.hrms.audit.dto.response.AuditEventDtoMapper;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.web.page.PageResponse;
import com.forward.hrms.commons.web.page.PageableDefaults;
import com.forward.hrms.commons.web.security.RequirePermission;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@Tag(name = "Audit — Read",
        description = "Compliance views over the full audit log. Requires `audit.read`.")
@RestController
@RequestMapping("/audit")
public class AuditController {

    private static final int MAX_LIMIT = 500;
    private static final int DEFAULT_LIMIT = 100;

    private final AuditEventRepository repo;
    private final ObjectMapper json;
    private final FacetsService facets;

    public AuditController(AuditEventRepository repo,
                           ObjectMapper json,
                           FacetsService facets) {
        this.repo = repo;
        this.json = json;
        this.facets = facets;
    }

    @Operation(summary = "Full history for one entity (cursor-paginated)",
            description = "Newest first. Use `from`/`to` to bound; `limit` caps page size (default 100, max 500). "
                    + "Pass `cursor` from the previous response's `nextCursor` to fetch the next page; "
                    + "`nextCursor` is null when the list is exhausted. Requires `audit.read`.")
    @ApiResponse(responseCode = "200", description = "Page of audit events for the entity.")
    @GetMapping("/by-entity/{entityType}/{entityId}")
    @RequirePermission("audit.read")
    public CursorPage<AuditEventDto> byEntity(
            @Parameter(description = "Entity type.", example = "employee") @PathVariable String entityType,
            @Parameter(description = "Entity id.", example = "1057") @PathVariable String entityId,
            @Parameter(description = "Inclusive lower bound (ISO-8601 instant).")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @Parameter(description = "Exclusive upper bound (ISO-8601 instant).")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @Parameter(description = "Page size (default 100, max 500).")
            @RequestParam(defaultValue = "100") int limit,
            @Parameter(description = "Opaque cursor from a previous response.")
            @RequestParam(required = false) String cursor) {
        int size = clamp(limit);
        EntityHistoryCursor c = EntityHistoryCursor.parse(cursor);
        List<AuditEvent> rows = repo.findByEntityCursor(entityType, entityId, from, to,
                c == null ? null : c.ts(),
                c == null ? null : c.id(),
                org.springframework.data.domain.PageRequest.of(0, size + 1));
        String next = EntityHistoryCursor.nextCursorFor(rows, size);
        List<AuditEventDto> page = rows.stream().limit(size).map(e -> AuditEventDtoMapper.of(e, json)).toList();
        return new CursorPage<>(page, next);
    }

    @Operation(summary = "Audit events authored by a user",
            description = "Standard `page`, `size`, `sort` query params apply. Requires `audit.read`.")
    @GetMapping("/by-actor/{userId}")
    @RequirePermission("audit.read")
    public PageResponse<AuditEventDto> byActor(
            @Parameter(description = "Actor user id.", example = "42") @PathVariable long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @Parameter(hidden = true) Pageable pageable) {
        return PageResponse.from(repo.findByActor(userId, from, to, pageable)
                .map(e -> AuditEventDtoMapper.of(e, json)));
    }

    @Operation(summary = "All events sharing a correlation id",
            description = "Paginated reconstruction of a single request's footprint across services. " +
                    "Default page size 200, max 500 — pathological correlations (long-running sagas " +
                    "with hundreds of audit emits) need cursoring. Requires `audit.read`.")
    @GetMapping("/by-correlation/{correlationId}")
    @RequirePermission("audit.read")
    public PageResponse<AuditEventDto> byCorrelation(
            @Parameter(description = "Correlation id.") @PathVariable String correlationId,
            @Parameter(hidden = true) Pageable pageable) {
        Pageable p = PageableDefaults.apply(pageable, 200, 500,
                Sort.by("occurredAt").ascending());
        return PageResponse.from(
                repo.findAllByCorrelationIdOrderByOccurredAtAsc(correlationId, p)
                        .map(e -> AuditEventDtoMapper.of(e, json)));
    }

    /** Default window when caller omits {@code from}: most recent 90 days. */
    private static final java.time.Duration SEARCH_DEFAULT_WINDOW = java.time.Duration.ofDays(90);

    /** Hard cap on (to − from): 1 year. Wider windows trip the brownout guard. */
    private static final java.time.Duration SEARCH_MAX_WINDOW = java.time.Duration.ofDays(366);

    @Operation(summary = "Search audit events",
            description = "Substring match on `message` (case-insensitive) plus optional filters on entity type, "
                    + "action, correlationId, actorUserId, userVisible, and time range. Standard `page`, `size`, "
                    + "`sort` apply (default sort: `occurredAt,desc`). All filters are AND-combined; pass only "
                    + "what you need. The drawer's \"View by correlation\" deep-link uses `correlationId`. "
                    + "Time-window guard (§6.7): when `from` is omitted it defaults to 90 days back; the "
                    + "`(from..to)` window is capped at 1 year. Requires `audit.read`.")
    @ApiResponse(responseCode = "422",
            description = "Time window > 1 year — narrow `from`/`to` and retry.")
    @GetMapping("/search")
    @RequirePermission("audit.read")
    public PageResponse<AuditEventDto> search(
            @Parameter(description = "Free-text substring matched against `message`.")
            @RequestParam(required = false) String q,
            @Parameter(description = "Filter by entity type.")
            @RequestParam(required = false) String entityType,
            @Parameter(description = "Filter by action.")
            @RequestParam(required = false) String action,
            @Parameter(description = "Exact correlation-id match — supports the drawer's \"View by correlation\" "
                    + "deep-link without falling back to /audit/by-correlation/{id}.")
            @RequestParam(required = false) String correlationId,
            @Parameter(description = "Exact actor user id match. Same data as /audit/by-actor/{id} but composable "
                    + "with the other filters.")
            @RequestParam(required = false) Long actorUserId,
            @Parameter(description = "Tri-state: omit for all, true for user-facing, false for system-only.")
            @RequestParam(required = false) Boolean userVisible,
            @Parameter(description = "Window start (inclusive). Defaults to 90 days back when omitted.")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @Parameter(description = "Window end (inclusive). Defaults to now.")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @Parameter(hidden = true) Pageable pageable) {
        LocalDateTime end = to == null ? LocalDateTime.now() : to;
        LocalDateTime start = from == null ? end.minus(SEARCH_DEFAULT_WINDOW) : from;
        if (java.time.Duration.between(start, end).compareTo(SEARCH_MAX_WINDOW) > 0) {
            throw new com.forward.hrms.commons.core.error.UnprocessableError(
                    com.forward.hrms.audit.enums.AuditErrorCode.SEARCH_WINDOW_TOO_WIDE,
                    "audit /search window must be ≤ 1 year; got "
                            + java.time.Duration.between(start, end).toDays() + " days");
        }
        Pageable p = PageableDefaults.apply(pageable, 50, 500,
                Sort.by(Sort.Direction.DESC, "occurredAt"));
        return PageResponse.from(repo.findAll(
                AuditEventSpecifications.search(blankToNull(q), blankToNull(entityType), blankToNull(action),
                        blankToNull(correlationId), actorUserId, userVisible, start, end),
                p
        ).map(e -> AuditEventDtoMapper.of(e, json)));
    }

    @Operation(summary = "Distinct facet values for the explorer",
            description = "Observed entity types, actions, and source services with frequencies. Cached for "
                    + "5 minutes per process — fine for the explorer's filter dropdowns. Requires `audit.read`.")
    @ApiResponse(responseCode = "200", description = "Facets, ordered by frequency desc.")
    @GetMapping("/facets")
    @RequirePermission("audit.read")
    public FacetsResponse facets() {
        return facets.get();
    }

    @Operation(summary = "Single audit event by id",
            description = "Returns the full event including before/after/changes/metadata. The drawer hits this "
                    + "for the \"open detail\" path. Owning audit-service lets us add redaction policy here later "
                    + "without touching producers. Requires `audit.read`.")
    @ApiResponse(responseCode = "200", description = "Audit event found.")
    @ApiResponse(responseCode = "404", description = "No event with that id.")
    @GetMapping("/events/{id}")
    @RequirePermission("audit.read")
    public AuditEventDto event(
            @Parameter(description = "Event id.", example = "5421") @PathVariable long id) {
        return repo.findById(id)
                .map(e -> AuditEventDtoMapper.of(e, json))
                .orElseThrow(() -> new NotFoundError(AuditErrorCode.EVENT_NOT_FOUND, "audit event not found: " + id));
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s; }

    private static int clamp(int requested) {
        if (requested <= 0) return DEFAULT_LIMIT;
        return Math.min(requested, MAX_LIMIT);
    }
}
