package com.forward.hrms.bff.controller;

import com.forward.hrms.bff.dto.response.SearchResponse;
import com.forward.hrms.bff.service.search.SearchAggregator;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;

@Tag(name = "Search — Command palette",
        description = "Single aggregated endpoint behind the topbar command palette (⌘K). " +
                "Fans out to every registered provider the caller can read, merges results, " +
                "re-ranks globally, and caps to the requested limit. Per-provider deadline 250 ms; " +
                "whole-request ceiling 600 ms — partial responses are flagged via `partial: true`.")
@RestController
@RequestMapping("/bff")
@RequiredArgsConstructor
public class SearchController {

    private final SearchAggregator aggregator;

    @Operation(summary = "Aggregated palette search",
            description = "Returns hits across employee, payslip-mine, leave-mine, lookup-*, org-node, " +
                    "and inbox-item providers (each landed independently). Server-side query length " +
                    "minimum is 2; below that the endpoint returns `{results: [], partial: false, " +
                    "providers: []}`. Results are caller-scoped — callers without a permission's " +
                    "subject simply don't see those provider rows; self-only providers (`*-mine`) " +
                    "always return only the caller's own data.")
    @ApiResponse(responseCode = "200", description = "Merged hits + per-provider diagnostics. " +
            "Cache-Control: private, max-age=10.")
    @ApiResponse(responseCode = "401", description = "No authenticated caller.")
    @GetMapping("/search")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SearchResponse> search(
            @Parameter(description = "Search query. Server-side minimum length is 2; the FE skips below 2.")
            @RequestParam String q,
            @Parameter(description = "CSV of provider types to consult. Omit to consult every " +
                    "provider the caller can read.",
                    example = "employee,payslip-mine")
            @RequestParam(required = false) String types,
            @Parameter(description = "Per-provider hit cap. Default 5, hard ceiling 10.")
            @RequestParam(required = false) Integer limit) {
        SearchResponse body = aggregator.search(q, parseTypes(types), limit);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofSeconds(10)).cachePrivate())
                .body(body);
    }

    private static Set<String> parseTypes(String csv) {
        if (csv == null || csv.isBlank()) return Set.of();
        Set<String> out = new LinkedHashSet<>();
        for (String t : csv.split(",")) {
            String trimmed = t.trim();
            if (!trimmed.isEmpty()) out.add(trimmed);
        }
        return out;
    }
}
