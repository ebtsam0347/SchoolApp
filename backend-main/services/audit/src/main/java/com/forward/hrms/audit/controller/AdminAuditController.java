package com.forward.hrms.audit.controller;

import com.forward.hrms.audit.dto.request.ReplayRequest;
import com.forward.hrms.audit.dto.response.AuditDeadLetterDto;
import com.forward.hrms.audit.dto.response.DeadLetterReplayResult;
import com.forward.hrms.audit.dto.response.HealthResponse;
import com.forward.hrms.audit.dto.response.ReplayResult;
import com.forward.hrms.audit.entity.AuditDeadLetter;
import com.forward.hrms.audit.enums.SkipReason;
import com.forward.hrms.audit.service.AuditDeadLetterService;
import com.forward.hrms.audit.service.AuditHealthService;
import com.forward.hrms.audit.service.ReplayService;
import com.forward.hrms.commons.web.page.PageResponse;
import com.forward.hrms.commons.web.page.PageableDefaults;
import com.forward.hrms.commons.web.security.RequirePermission;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@Tag(name = "Admin — Audit",
        description = "Bounded replay tool, dead-letter triage, and consumer-lag health view. Requires `audit.admin`.")
@RestController
@RequestMapping("/admin/audit")
@RequiredArgsConstructor
public class AdminAuditController {

    private final ReplayService replay;
    private final AuditHealthService health;
    private final AuditDeadLetterService deadLetters;

    @Operation(summary = "Replay a bounded Kafka offset range",
            description = "Idempotent ingest makes duplicates safe — they're counted as `skipped`. "
                    + "Use `dryRun = true` to size the work first. Requires `audit.admin`.")
    @ApiResponse(responseCode = "200", description = "Replay outcome.")
    @ApiResponse(responseCode = "400", description = "Unknown topic, or invalid offset range.")
    @ApiResponse(responseCode = "422", description = "Topic has no partitions matching the request.")
    @PostMapping("/replay")
    @RequirePermission("audit.admin")
    public ReplayResult replay(@Valid @RequestBody ReplayRequest req) {
        return replay.replay(req);
    }

    @Operation(summary = "Consumer health per audit topic",
            description = "Per-topic and per-partition end offsets, committed offsets, and lag. Requires `audit.admin`.")
    @GetMapping("/health")
    @RequirePermission("audit.admin")
    public HealthResponse health() {
        return health.snapshot();
    }

    @Operation(summary = "List parked envelopes",
            description = "Filter by source service, reason, and lower bound on `receivedAt`. "
                    + "Listing projection drops `rawPayload` to keep responses small — fetch the full row "
                    + "via `GET /admin/audit/dead-letters/{id}`. Requires `audit.admin`.")
    @ApiResponse(responseCode = "200", description = "Page of dead-letter rows, newest first.")
    @GetMapping("/dead-letters")
    @RequirePermission("audit.admin")
    public PageResponse<AuditDeadLetterDto> listDeadLetters(
            @Parameter(description = "Filter by source service (auth, employee, …).")
            @RequestParam(required = false) String source,
            @Parameter(description = "Filter by skip reason.")
            @RequestParam(required = false) SkipReason reason,
            @Parameter(description = "Inclusive lower bound on receivedAt (ISO-8601).")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since,
            @Parameter(hidden = true) Pageable pageable) {
        Pageable p = PageableDefaults.apply(pageable, 50, 200, Sort.unsorted());
        Page<AuditDeadLetter> rows = deadLetters.search(source, reason, since, p);
        return PageResponse.from(rows.map(AuditDeadLetterDto::summary));
    }

    @Operation(summary = "Fetch one dead-letter row (full raw payload)",
            description = "Includes the original envelope JSON. Requires `audit.admin`.")
    @ApiResponse(responseCode = "200", description = "Dead-letter row.")
    @ApiResponse(responseCode = "404", description = "No row with that id.")
    @GetMapping("/dead-letters/{id}")
    @RequirePermission("audit.admin")
    public AuditDeadLetterDto getDeadLetter(@PathVariable long id) {
        return AuditDeadLetterDto.of(deadLetters.get(id));
    }

    @Operation(summary = "Replay one dead-letter row",
            description = "Re-runs the stored envelope through ingest. On INSERTED/DUPLICATE the "
                    + "dead-letter row is deleted; on STILL_FAILING the row is updated with the new "
                    + "reason and the attempt counter advances. Requires `audit.admin`.")
    @ApiResponse(responseCode = "200", description = "Replay outcome.")
    @ApiResponse(responseCode = "404", description = "No row with that id.")
    @PostMapping("/dead-letters/{id}/replay")
    @RequirePermission("audit.admin")
    public DeadLetterReplayResult replayDeadLetter(@PathVariable long id) {
        return deadLetters.replay(id);
    }

    @Operation(summary = "Discard one dead-letter row",
            description = "Permanently drops the parked envelope. Use when ingest can never succeed "
                    + "(e.g. the data is genuinely corrupt and not worth recovering). Requires `audit.admin`.")
    @ApiResponse(responseCode = "204", description = "Discarded.")
    @ApiResponse(responseCode = "404", description = "No row with that id.")
    @DeleteMapping("/dead-letters/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequirePermission("audit.admin")
    public void discardDeadLetter(@PathVariable long id) {
        deadLetters.discard(id);
    }
}
