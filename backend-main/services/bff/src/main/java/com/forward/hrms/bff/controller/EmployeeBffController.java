package com.forward.hrms.bff.controller;

import com.forward.hrms.audit.dto.response.TimelinePage;
import com.forward.hrms.bff.dto.response.AccountProfile;
import com.forward.hrms.bff.dto.response.EmployeeDetail;
import com.forward.hrms.bff.dto.response.EmployeesByIdsResponse;
import com.forward.hrms.bff.service.EmployeeBffService;
import com.forward.hrms.commons.core.error.CommonErrorCode;
import com.forward.hrms.commons.core.error.UnprocessableError;
import com.forward.hrms.commons.web.security.RequirePermission;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Employee — Composites",
        description = "Cross-service composites that join employee with audit (timelines) " +
                "and the shared identity cache (by-ids). Single-service composites moved " +
                "into employee-service in Step 4 of docs/caching.md (directory, stats, " +
                "history-resolved, org-tree, delegation-candidates, onboarding-lookups).")
@RestController
@RequestMapping("/bff")
@RequiredArgsConstructor
public class EmployeeBffController {

    private final EmployeeBffService service;

    @Operation(summary = "Aggregated /people/{id} payload",
            description = "Returns employee + resolved labels + active contract + active suspension " +
                    "+ most-recent exit row + UPHELD warning counts + first-8 direct reports + last 20 " +
                    "user-visible timeline events + last 10 assignment-history rows. Replaces ~10 " +
                    "round-trips today. For the full paginated history use " +
                    "GET /employees/{id}/history/resolved on employee-service. Requires `employee.read`.")
    @ApiResponse(responseCode = "200", description = "Composite payload assembled from employee + audit.")
    @ApiResponse(responseCode = "404", description = "Employee not found.")
    @ApiResponse(responseCode = "502", description = "A required downstream was unreachable.")
    @GetMapping("/employee/{id}")
    @RequirePermission("employee.read")
    public EmployeeDetail employee(@Parameter(description = "Employee id") @PathVariable long id) {
        return service.employeeDetail(id);
    }

    @Operation(summary = "Merged employee timeline",
            description = "Merges the audit /timeline rows for `employee/{id}` with the user " +
                    "timeline for the linked `user/{userId}` (when the employee has ESS). Newest first; " +
                    "cursor pagination via the audit-service cursor on the entity feed. Requires `employee.read`.")
    @GetMapping("/employees/{id}/timeline")
    @RequirePermission("employee.read")
    public TimelinePage timeline(
            @PathVariable long id,
            @Parameter(description = "Page size (default 50, max 200).")
            @RequestParam(required = false) Integer limit,
            @Parameter(description = "Opaque cursor from a previous response's `nextCursor`.")
            @RequestParam(required = false) String cursor) {
        return service.timeline(id, limit, cursor);
    }

    @Operation(summary = "Resolve employee summaries by id list",
            description = "Generic name + photo + department resolver for any list of bare employee " +
                    "ids. Used by approvals inbox, OT batch rows, and any surface that comes back " +
                    "keyed by employee id without names attached. De-duplicates the input. Caps at " +
                    EmployeeBffService.BY_IDS_MAX + " ids per call — split larger lists client-side." +
                    " Requires `employee.read`.")
    @ApiResponse(responseCode = "200", description = "Resolved summaries; missing ids dropped silently.")
    @ApiResponse(responseCode = "422", description = "More than " + EmployeeBffService.BY_IDS_MAX + " ids.")
    @GetMapping("/employees/by-ids")
    @RequirePermission("employee.read")
    public EmployeesByIdsResponse byIds(
            @Parameter(description = "Comma-separated employee ids; capped at "
                    + EmployeeBffService.BY_IDS_MAX + " per call.")
            @RequestParam("ids") List<Long> ids) {
        if (ids != null && ids.size() > EmployeeBffService.BY_IDS_MAX) {
            throw new UnprocessableError(CommonErrorCode.UNPROCESSABLE,
                    "too many ids (max " + EmployeeBffService.BY_IDS_MAX + ")");
        }
        return service.byIds(ids);
    }

    @Operation(summary = "Aggregated /account/profile payload",
            description = "ESS counterpart to /bff/employee/{id} — single round-trip for the signed-in " +
                    "employee's profile tab.")
    @GetMapping("/account/profile")
    @PreAuthorize("isAuthenticated()")
    public AccountProfile accountProfile() {
        return service.accountProfile();
    }
}
