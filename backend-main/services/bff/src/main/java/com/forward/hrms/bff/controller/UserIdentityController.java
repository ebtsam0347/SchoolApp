package com.forward.hrms.bff.controller;

import com.forward.hrms.bff.dto.response.UsersByIdsResponse;
import com.forward.hrms.bff.service.UserIdentityService;
import com.forward.hrms.commons.core.error.CommonErrorCode;
import com.forward.hrms.commons.core.error.UnprocessableError;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "BFF — Users",
        description = "Composite identity resolver for chat / mention chips. Joins auth (username + " +
                "fullName) with employee (avatar) in one call so the FE doesn't have to. " +
                "Authenticated only — no `auth.users.read` / `employee.read` required.")
@RestController
@RequestMapping("/bff/users")
@RequiredArgsConstructor
public class UserIdentityController {

    private final UserIdentityService service;

    @Operation(summary = "Resolve userIds → display identity",
            description = "Order matches input, deduped. Unknown ids are dropped silently. " +
                    "Avatar is null for admin-only users with no employee row. Cap: " +
                    UserIdentityService.BY_IDS_MAX + " ids per call.")
    @ApiResponse(responseCode = "200")
    @ApiResponse(responseCode = "422", description = "Too many ids in a single request.")
    @GetMapping("/by-ids")
    public UsersByIdsResponse byIds(
            @Parameter(description = "Comma-separated user ids; max " + UserIdentityService.BY_IDS_MAX + " per call.")
            @RequestParam("ids") List<Long> ids) {
        if (ids != null && ids.size() > UserIdentityService.BY_IDS_MAX) {
            throw new UnprocessableError(CommonErrorCode.UNPROCESSABLE,
                    "too many ids (max " + UserIdentityService.BY_IDS_MAX + ")");
        }
        return service.byIds(ids);
    }

    @Operation(summary = "Search active users for chat pickers",
            description = "Substring match on username + fullName. ACTIVE only — terminated/disabled " +
                    "accounts are filtered out. Returns the same shape as `/by-ids`. Cap: " +
                    UserIdentityService.SEARCH_MAX_SIZE + " rows per call.")
    @ApiResponse(responseCode = "200")
    @GetMapping("/search")
    public UsersByIdsResponse search(
            @Parameter(description = "Substring; min 1 char.") @RequestParam("q") String q,
            @Parameter(description = "Max rows (default 20, capped at " + UserIdentityService.SEARCH_MAX_SIZE + ").")
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return service.search(q, size);
    }
}
