package com.forward.hrms.employee.controller.composite;

import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.core.error.UnauthorizedError;
import com.forward.hrms.commons.web.page.PageResponse;
import com.forward.hrms.employee.dto.response.DelegationCandidatesResponse;
import com.forward.hrms.employee.dto.response.EmployeeDirectoryRow;
import com.forward.hrms.employee.dto.response.EmployeeHistoryRow;
import com.forward.hrms.employee.dto.response.EmployeeStats;
import com.forward.hrms.employee.dto.response.OnboardingLookups;
import com.forward.hrms.employee.dto.response.OrgTree;
import com.forward.hrms.employee.enums.EmployeeErrorCode;
import com.forward.hrms.employee.enums.EmploymentStatus;
import com.forward.hrms.employee.enums.EmploymentType;
import com.forward.hrms.employee.repository.EmployeeRepository;
import com.forward.hrms.employee.service.composite.EmployeeCompositesService;
import com.forward.hrms.commons.web.security.RequirePermission;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * FE-facing composite endpoints for employee-only surfaces. Each
 * method joins employee rows with the catalog labels the page needs,
 * served by employee-service in-process. Replaces the BFF's
 * single-service composites that used to fan out via HTTP.
 *
 * <p>Cross-service composites (employee + audit timeline,
 * employee + payroll dashboards, /people/{id} detail) stay in BFF.
 */
@Tag(name = "Employees — Composites",
        description = "Read-only composites that join employee rows with catalog labels. " +
                "Replaces the legacy BFF composites that fanned out via HTTP for what is " +
                "ultimately one service's data.")
@RestController
@RequiredArgsConstructor
public class EmployeeCompositesController {

    private final EmployeeCompositesService service;
    private final EmployeeRepository employees;

    @Operation(summary = "Directory list with denormalized labels",
            description = "Same filters as `/employees`. Each row carries catalog labels " +
                    "(department / section / designation / grade / city) and a manager " +
                    "summary so the FE doesn't have to fan out. Requires `employee.read`.")
    @GetMapping("/employees/directory")
    @RequirePermission("employee.read")
    public PageResponse<EmployeeDirectoryRow> directory(
            @RequestParam(required = false) EmploymentStatus status,
            @RequestParam(required = false) EmploymentType employmentType,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long sectionId,
            @RequestParam(required = false) Long managerId,
            @RequestParam(required = false) Long gradeId,
            @RequestParam(required = false) Long designationId,
            @RequestParam(required = false) Boolean hasEss,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer probationDueWithin,
            @RequestParam(required = false) Boolean probationOverdue,
            @RequestParam(required = false, defaultValue = "false") boolean includeTerminated,
            @Parameter(hidden = true) Pageable pageable) {
        return service.directory(status, employmentType, departmentId, sectionId, managerId,
                gradeId, designationId, hasEss, q, probationDueWithin, probationOverdue,
                includeTerminated, pageable);
    }

    @Operation(summary = "Headline KPIs for the People-overview screen",
            description = "Counts by status / employmentType, probation windows " +
                    "(active / due-in-14d / overdue), certification windows " +
                    "(expiring-in-30d / expired), and ESS coverage. Requires `employee.read`.")
    @GetMapping("/employees/stats")
    @RequirePermission("employee.read")
    public EmployeeStats stats() {
        return service.stats();
    }

    @Operation(summary = "Paginated employment history with resolved labels",
            description = "Sibling to `/employees/{id}/history` — same rows, with " +
                    "catalog labels and manager summaries denormalized server-side. " +
                    "Newest first. Requires `employee.read`.")
    @GetMapping("/employees/{id}/history/resolved")
    @RequirePermission("employee.read")
    public PageResponse<EmployeeHistoryRow> historyResolved(
            @PathVariable long id,
            @Parameter(description = "Page size (default 20, max 200).")
            @RequestParam(required = false) Integer size) {
        return service.historyResolved(id, size);
    }

    @Operation(summary = "Recursive org chart",
            description = "Manager → reports tree from the given root (or top-of-org " +
                    "when `root` is omitted). `depth` defaults to 3, hard-capped to 6. " +
                    "Requires `employee.read`.")
    @GetMapping("/employees/org-tree")
    @RequirePermission("employee.read")
    public OrgTree orgTree(
            @Parameter(description = "Root employee id. Omit to start at top-of-org.")
            @RequestParam(required = false) Long root,
            @Parameter(description = "Tree depth (default 3, max 6).")
            @RequestParam(required = false) Integer depth) {
        return service.orgTree(root, depth);
    }

    @Operation(summary = "Delegate-picker for the approval-delegation drawer",
            description = "ACTIVE employees with ESS, excluding the caller. Optional `q` " +
                    "substring-matches name/employee code. `limit` defaults to "
                    + EmployeeCompositesService.DELEGATION_CANDIDATES_DEFAULT + " (max "
                    + EmployeeCompositesService.DELEGATION_CANDIDATES_MAX + ").")
    @GetMapping("/employees/delegation-candidates")
    @PreAuthorize("isAuthenticated()")
    public DelegationCandidatesResponse delegationCandidates(
            @Parameter(description = "Substring search across fullName / employeeCode.")
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer limit) {
        return service.delegationCandidates(currentEmployeeId(), q, limit);
    }

    @Operation(summary = "Bundle of every catalog the onboarding wizard needs on first paint",
            description = "Departments, sections, designations, grades, provinces, cities, " +
                    "qualifications, skills. The FE fetches `/admin/roles` from auth-service " +
                    "separately when the caller has `auth.users.write`.")
    @GetMapping("/employees/onboarding-lookups")
    @PreAuthorize("isAuthenticated()")
    public OnboardingLookups onboardingLookups() {
        return service.onboardingLookups();
    }

    private long currentEmployeeId() {
        CurrentUser u = CurrentUserHolder.get()
                .orElseThrow(() -> new UnauthorizedError(
                        com.forward.hrms.commons.core.error.CommonErrorCode.UNAUTHENTICATED,
                        "no authenticated user"));
        return employees.findByUserId(u.userId())
                .orElseThrow(() -> new NotFoundError(
                        EmployeeErrorCode.EMPLOYEE_NO_ESS,
                        "no employee linked to this user"))
                .getId();
    }
}
