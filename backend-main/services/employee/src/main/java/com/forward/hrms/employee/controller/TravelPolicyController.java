package com.forward.hrms.employee.controller;

import com.forward.hrms.commons.web.page.PageResponse;
import com.forward.hrms.commons.web.page.PageableDefaults;
import com.forward.hrms.commons.web.security.RequirePermission;
import com.forward.hrms.employee.dto.request.travel.request.CreateTravelPolicyGradeLimitRequest;
import com.forward.hrms.employee.dto.request.travel.request.CreateTravelPolicyVersionRequest;
import com.forward.hrms.employee.dto.request.travel.response.TravelPolicyGradeLimitResponse;
import com.forward.hrms.employee.dto.request.travel.response.TravelPolicyVersionResponse;
import com.forward.hrms.employee.enums.travel.TravelPolicyVersionStatus;
import com.forward.hrms.employee.service.travel.TravelPolicyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Travel Policy Administration",
        description = "Versioned travel policies and their grade-specific entitlement limits. " +
                "Lifecycle: DRAFT → ACTIVE → INACTIVE → RETIRED; a version and its grade limits are " +
                "editable only while DRAFT. Reads require `travel.read`; all mutations require `travel.admin`.")
@RestController
@RequiredArgsConstructor
@RequestMapping("/travel/policies")
public class TravelPolicyController {

    private final TravelPolicyService service;

    // ── policy versions ──────────────────────────────────────────────────────

    @Operation(summary = "Create a policy version (starts in DRAFT)",
            description = "Requires `travel.admin`. The version number is assigned automatically per policy code. " +
                    "`policyCode` must be a supported travel policy family - `LOCAL_FACTORY_TRAVEL` or " +
                    "`INTERNATIONAL_FACTORY_TRAVEL`.")
    @ApiResponse(responseCode = "201", description = "Policy version created.")
    @ApiResponse(responseCode = "409", description = "A version with the allocated number already exists "
            + "(concurrent create) - retry.")
    @ApiResponse(responseCode = "422", description = "`policyCode` is not a supported travel policy family.")
    @PostMapping
    @RequirePermission("travel.admin")
    @ResponseStatus(HttpStatus.CREATED)
    public TravelPolicyVersionResponse createVersion(@Valid @RequestBody CreateTravelPolicyVersionRequest req) {
        return service.createPolicyVersion(req);
    }

    @Operation(summary = "Get a policy version", description = "Requires `travel.read`.")
    @ApiResponse(responseCode = "404", description = "Policy version not found.")
    @GetMapping("/{id}")
    @RequirePermission("travel.read")
    public TravelPolicyVersionResponse getVersion(@PathVariable Long id) {
        return service.getPolicyVersion(id);
    }

    @Operation(summary = "Search policy versions (paginated)",
            description = "Both filters optional. Requires `travel.read`.")
    @GetMapping
    @RequirePermission("travel.read")
    public PageResponse<TravelPolicyVersionResponse> searchVersions(
            @Parameter(description = "Filter by policy code.") @RequestParam(required = false) String policyCode,
            @Parameter(description = "Filter by lifecycle status.")
            @RequestParam(required = false) TravelPolicyVersionStatus status,
            @Parameter(hidden = true) Pageable pageable) {
        return PageResponse.from(service.searchPolicyVersions(
                policyCode, status, PageableDefaults.apply(pageable, "versionNo")));
    }

    @Operation(summary = "List every version of one policy (newest first)",
            description = "Requires `travel.read`.")
    @GetMapping("/by-code/{policyCode}")
    @RequirePermission("travel.read")
    public List<TravelPolicyVersionResponse> listVersionsOfPolicy(@PathVariable String policyCode) {
        return service.listVersionsOfPolicy(policyCode);
    }

    @Operation(summary = "Update a DRAFT policy version",
            description = "Requires `travel.admin`. `policyCode` is immutable.")
    @ApiResponse(responseCode = "404", description = "Policy version not found.")
    @ApiResponse(responseCode = "409", description = "Policy version is not in DRAFT status.")
    @PatchMapping("/{id}")
    @RequirePermission("travel.admin")
    public TravelPolicyVersionResponse updateVersion(@PathVariable Long id,
                                                     @Valid @RequestBody CreateTravelPolicyVersionRequest req) {
        return service.updatePolicyVersion(id, req);
    }

    @Operation(summary = "Activate a DRAFT policy version (DRAFT → ACTIVE)", description = "Requires `travel.admin`.")
    @ApiResponse(responseCode = "409", description = "Policy version is not in DRAFT status.")
    @PostMapping("/{id}/activate")
    @RequirePermission("travel.admin")
    public TravelPolicyVersionResponse activateVersion(@PathVariable Long id) {
        return service.activatePolicyVersion(id);
    }

    @Operation(summary = "Deactivate a policy version (ACTIVE → INACTIVE)", description = "Requires `travel.admin`.")
    @ApiResponse(responseCode = "409", description = "Policy version is not in ACTIVE status.")
    @PostMapping("/{id}/deactivate")
    @RequirePermission("travel.admin")
    public TravelPolicyVersionResponse deactivateVersion(@PathVariable Long id) {
        return service.deactivatePolicyVersion(id);
    }

    @Operation(summary = "Retire a policy version (ACTIVE/INACTIVE → RETIRED)", description = "Requires `travel.admin`.")
    @ApiResponse(responseCode = "409", description = "Policy version cannot be retired in its current status.")
    @PostMapping("/{id}/retire")
    @RequirePermission("travel.admin")
    public TravelPolicyVersionResponse retireVersion(@PathVariable Long id) {
        return service.retirePolicyVersion(id);
    }

    // ── grade limits ─────────────────────────────────────────────────────────
    @Operation(summary = "Add a grade limit to a DRAFT policy version",
            description = "Requires `travel.admin`. One limit per (grade, expense type) per version.")
    @ApiResponse(responseCode = "201", description = "Grade limit created.")
    @ApiResponse(responseCode = "404", description = "Policy version or grade not found.")
    @ApiResponse(responseCode = "409", description = "Version is not DRAFT, or a duplicate limit exists.")
    @PostMapping("/{policyVersionId}/grade-limits")
    @RequirePermission("travel.admin")
    @ResponseStatus(HttpStatus.CREATED)
    public TravelPolicyGradeLimitResponse addGradeLimit(@PathVariable Long policyVersionId,
                                                        @Valid @RequestBody CreateTravelPolicyGradeLimitRequest req) {
        return service.addGradeLimit(policyVersionId, req);
    }

    @Operation(summary = "List the grade limits of a policy version", description = "Requires `travel.read`.")
    @ApiResponse(responseCode = "404", description = "Policy version not found.")
    @GetMapping("/{policyVersionId}/grade-limits")
    @RequirePermission("travel.read")
    public List<TravelPolicyGradeLimitResponse> listGradeLimits(@PathVariable Long policyVersionId) {
        return service.listGradeLimits(policyVersionId);
    }

    @Operation(summary = "Update a grade limit (parent version must be DRAFT)", description = "Requires `travel.admin`.")
    @ApiResponse(responseCode = "404", description = "Grade limit or grade not found.")
    @ApiResponse(responseCode = "409", description = "Parent version is not DRAFT, or a duplicate limit exists.")
    @PatchMapping("/grade-limits/{id}")
    @RequirePermission("travel.admin")
    public TravelPolicyGradeLimitResponse updateGradeLimit(@PathVariable Long id,
                                                           @Valid @RequestBody CreateTravelPolicyGradeLimitRequest req) {
        return service.updateGradeLimit(id, req);
    }

    @Operation(summary = "Delete a grade limit (parent version must be DRAFT)", description = "Requires `travel.admin`.")
    @ApiResponse(responseCode = "404", description = "Grade limit not found.")
    @ApiResponse(responseCode = "409", description = "Parent version is not DRAFT.")
    @DeleteMapping("/grade-limits/{id}")
    @RequirePermission("travel.admin")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteGradeLimit(@PathVariable Long id) {
        service.deleteGradeLimit(id);
    }
}
