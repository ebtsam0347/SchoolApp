package com.forward.hrms.auth.controller;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.forward.hrms.auth.dto.response.UserIdentitySummary;
import com.forward.hrms.auth.dto.response.UserIdentitySummaryMapper;
import com.forward.hrms.auth.repository.UserRepository;
import com.forward.hrms.commons.core.error.CommonErrorCode;
import com.forward.hrms.commons.core.error.UnprocessableError;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Users — Identity Resolver",
        description = "Authenticated-only batch lookup that returns the minimum identity fields " +
                "(username, fullName, status) every chat / directory chip needs. No PII; no " +
                "`auth.users.read` permission required — anyone signed in can resolve a userId " +
                "to a display handle.")
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserIdentityController {

    /** Hard cap to keep the IN-list bounded; matches the BFF resolver. */
    public static final int BY_IDS_MAX = 200;

    /** Page size cap for the identity-scoped search. */
    public static final int SEARCH_MAX_SIZE = 50;

    private final UserRepository users;

    @Operation(summary = "Resolve userIds to display identity",
            description = "Returns one row per existing user id, deduped and ordered to match the input. " +
                    "Unknown ids are silently dropped — the resolver isn't a `findById` and won't 404. " +
                    "Cap: " + BY_IDS_MAX + " ids per call.")
    @ApiResponse(responseCode = "200")
    @ApiResponse(responseCode = "422", description = "Too many ids in a single request.")
    @GetMapping("/by-ids")
    public List<UserIdentitySummary> byIds(
            @Parameter(description = "Comma-separated user ids; max " + BY_IDS_MAX + " per call.")
            @RequestParam("ids") List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        if (ids.size() > BY_IDS_MAX) {
            throw new UnprocessableError(CommonErrorCode.UNPROCESSABLE,
                    "too many ids (max " + BY_IDS_MAX + ")");
        }
        Set<Long> distinct = new HashSet<>(ids);
        return users.findAllById(distinct).stream()
                .map(UserIdentitySummaryMapper::of)
                .toList();
    }

    @Operation(summary = "Search active users by username or full name",
            description = "Case-insensitive substring match against username + fullName. ACTIVE only — " +
                    "DISABLED / LOCKED accounts are excluded so chat pickers don't surface ex-employees. " +
                    "Cap: " + SEARCH_MAX_SIZE + " rows per call.")
    @ApiResponse(responseCode = "200")
    @GetMapping("/search")
    public List<UserIdentitySummary> search(
            @Parameter(description = "Substring; min 1 char.") @RequestParam("q") String q,
            @Parameter(description = "Max rows (default 20, capped at " + SEARCH_MAX_SIZE + ").")
            @RequestParam(value = "size", defaultValue = "20") int size) {
        if (q == null || q.isBlank()) return List.of();
        int safeSize = Math.min(Math.max(size, 1), SEARCH_MAX_SIZE);
        return users.searchActive(q.trim(), PageRequest.of(0, safeSize)).stream()
                .map(UserIdentitySummaryMapper::of)
                .toList();
    }
}
