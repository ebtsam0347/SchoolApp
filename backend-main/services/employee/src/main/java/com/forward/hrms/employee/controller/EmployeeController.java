package com.forward.hrms.employee.controller;

import com.forward.hrms.commons.web.page.PageResponse;
import com.forward.hrms.commons.web.page.PageableDefaults;
import com.forward.hrms.commons.web.security.RequirePermission;
import com.forward.hrms.employee.dto.request.*;
import com.forward.hrms.employee.dto.response.*;
import com.forward.hrms.employee.enums.EmploymentStatus;
import com.forward.hrms.employee.service.ContractService;
import com.forward.hrms.employee.service.EmployeeService;
import com.forward.hrms.employee.service.EssService;
import com.forward.hrms.employee.service.OnboardingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Employees",
        description = "HR-facing employee CRUD, onboarding, assignment changes, and ESS grant/revoke. " +
                "Self-serve `/employees/me` lives on a separate controller. " +
                "Requires `employee.read` / `employee.write` / `employee.admin`.")
@RestController
@RequestMapping("/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final OnboardingService onboarding;
    private final EmployeeService employees;
    private final EssService ess;
    private final ContractService contracts;

    @Operation(summary = "Onboard an employee",
            description = "Single endpoint for both quick-add (modal) and complete-form (page) flows; "
                    + "quick-add sends only the required identity + assignment fields, complete-form additionally "
                    + "supplies salary, shift, and per-row arrays (qualifications, experience, skills, "
                    + "certifications, emergency contacts, disabilities). The kickoff transaction persists the "
                    + "employee + initial PROBATION contract + history seed + every supplied array, emits "
                    + "`employee.created` via outbox, and registers an ONBOARDING saga whose ordered steps "
                    + "(PROVISION_ESS, SET_BASIC_SALARY, ASSIGN_SALARY_COMPONENTS, SET_BANK_DETAILS, "
                    + "ASSIGN_SHIFT) call auth/payroll/attendance with bounded retry. Steps no-op when their "
                    + "section is absent. Response includes `sagaId` whenever any cross-service work was "
                    + "requested; the FE polls `/sagas/{id}` for per-step RUNNING / COMPLETED / FAILED. "
                    + "Requires `employee.write`.")
    @ApiResponse(responseCode = "201", description = "Employee created. `sagaId` set when any cross-service work was requested.")
    @ApiResponse(responseCode = "409", description = "Manager not found / catalog inactive / duplicate qualification/skill/disability in payload / section-department mismatch.")
    @ApiResponse(responseCode = "422", description = "Validation failure.")
    @PostMapping
    @RequirePermission("employee.write")
    @ResponseStatus(HttpStatus.CREATED)
    public OnboardEmployeeResponse onboard(@Valid @RequestBody OnboardEmployeeRequest req) {
        return onboarding.onboard(req);
    }

    @Operation(summary = "Get an employee by id", description = "Requires `employee.read`.")
    @ApiResponse(responseCode = "404", description = "Employee not found.")
    @GetMapping("/{id}")
    @RequirePermission("employee.read")
    public EmployeeDto get(@Parameter(description = "Employee id") @PathVariable long id) {
        return employees.get(id);
    }

    // new API to send response for final termination settlement with employee info
    @Operation(summary = "Get an employee by id for final termination settlement", description = "Requires `employee.read`.")
    @ApiResponse(responseCode = "404", description = "Employee not found.")
    @GetMapping("/{id}/final-termination-settlement")
    @RequirePermission("employee.read")
    public EmployeeTerminationInfoDto getForFinalTerminationSettlement(@Parameter(description = "Employee id") @PathVariable long id) {
        return employees.getForFinalTerminationSettlement(id);
    }

    @Operation(summary = "Employee picker (service-neutral)",
            description = "ACTIVE employees, name + code fuzzy match with relevance ordering, " +
                    "capped at 100. The single picker every typeahead surface should use " +
                    "(payroll off-cycle / increment / bonus, attendance operator punch / shift " +
                    "override, notification recipient picker, …). Auth-only.")
    @GetMapping("/picker")
    @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
    public com.forward.hrms.employee.dto.response.EmployeePickerResponse picker(
            @Parameter(description = "Substring search across fullName / employeeCode " +
                    "(also workEmail / phone / cnic). Empty returns first page by code.")
            @RequestParam(required = false) String q,
            @Parameter(description = "Max rows. Default 20, capped at 100.")
            @RequestParam(required = false) Integer limit) {
        return employees.picker(q, limit);
    }

    @Operation(summary = "Batch lookup by ids",
            description = "Returns the rows for {@code ids} in the requested order; " +
                    "missing ids are silently dropped. Caps the batch at 200 to keep the response " +
                    "bounded — callers needing more should page through `/employees`. " +
                    "Authenticated; no specific permission required so the commons-cache miss path " +
                    "works for every service.")
    @GetMapping("/by-ids")
    @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
    public java.util.List<EmployeeDto> byIds(
            @Parameter(description = "Employee ids; comma-separated or repeated query param.")
            @RequestParam java.util.Set<Long> ids) {
        if (ids.size() > 200) {
            throw new com.forward.hrms.commons.core.error.UnprocessableError(
                    com.forward.hrms.employee.enums.EmployeeErrorCode.VALIDATION_FAILED,
                    "ids batch capped at 200; got " + ids.size());
        }
        return employees.byIds(ids);
    }

    @Operation(summary = "Batch employment-status lookup by ids",
            description = "Returns only `id` + `employmentStatus` for {@code ids}; missing ids are " +
                    "silently dropped, terminated rows are included. Caps the batch at 300. " +
                    "Authenticated; no specific permission required so background jobs in other " +
                    "services (attendance initialization) can filter by status.")
    @ApiResponse(responseCode = "422", description = "More than 300 ids.")
    @GetMapping("/employment-statuses")
    @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
    public List<EmployeeStatusDto> employmentStatuses(
            @Parameter(description = "Employee ids; comma-separated or repeated query param.")
            @RequestParam java.util.Set<Long> ids) {
        if (ids.size() > 300) {
            throw new com.forward.hrms.commons.core.error.UnprocessableError(
                    com.forward.hrms.employee.enums.EmployeeErrorCode.VALIDATION_FAILED,
                    "ids batch capped at 300; got " + ids.size());
        }
        return employees.statusesByIds(ids);
    }

    @Operation(summary = "List employees (paginated)",
            description = "Standard Spring `page`, `size`, and `sort` query params apply. " +
                    "Sortable fields: `fullName`, `employeeCode`, `joiningDate`, `confirmationDate`, " +
                    "`departmentId`, `sectionId`, `gradeId`, `managerId`, `employmentStatus`, " +
                    "`employmentType`. " +
                    "**Defaults:** when `status` is omitted, TERMINATED rows are hidden — pass " +
                    "`status=TERMINATED` to list only terminated, or `includeTerminated=true` to " +
                    "see every status. When `sort` is omitted, results are ordered by `employeeCode` " +
                    "ascending. " +
                    "When `probationDueWithin` or `probationOverdue=true` is set, the response is " +
                    "narrowed to employees whose ACTIVE PROBATION contract matches the filter " +
                    "(other filters are ignored for these specialized queries) and each row carries " +
                    "an `activeProbation` summary. Requires `employee.read`.")
    @GetMapping
    @RequirePermission("employee.read")
    public PageResponse<EmployeeDto> list(
            @Parameter(description = "Filter by employment status. Omit to list every non-terminated row.") @RequestParam(required = false) EmploymentStatus status,
            @Parameter(description = "Filter by employment type.")
            @RequestParam(required = false) com.forward.hrms.employee.enums.EmploymentType employmentType,
            @Parameter(description = "Filter by department id.") @RequestParam(required = false) Long departmentId,
            @Parameter(description = "Filter by section id.") @RequestParam(required = false) Long sectionId,
            @Parameter(description = "Filter by direct manager id.") @RequestParam(required = false) Long managerId,
            @Parameter(description = "Filter by grade id.") @RequestParam(required = false) Long gradeId,
            @Parameter(description = "Filter by designation id.") @RequestParam(required = false) Long designationId,
            @Parameter(description = "True = only ESS-linked, false = only non-ESS.") @RequestParam(required = false) Boolean hasEss,
            @Parameter(description = "Case-insensitive substring search across fullName, employeeCode, " +
                    "workEmail, phone and cnic.")
            @RequestParam(required = false) String q,
            @Parameter(description = "Days from today; overdue rows (≤0 days remaining) are included.")
            @RequestParam(required = false) Integer probationDueWithin,
            @Parameter(description = "Set to true to list only overdue probation (end_date < today).")
            @RequestParam(required = false) Boolean probationOverdue,
            @Parameter(description = "Set to true to include TERMINATED rows when no explicit `status` filter is set.")
            @RequestParam(required = false, defaultValue = "false") boolean includeTerminated,
            @Parameter(hidden = true) Pageable pageable) {
        // When q is non-empty the spec installs a relevance ORDER BY; the
        // default code-asc sort would otherwise overwrite it via Spring
        // Data's applySorting and the BFF palette would only ever see the
        // five lexically-smallest matching codes.
        org.springframework.data.domain.Sort defaultSort = (q != null && !q.isBlank())
                ? org.springframework.data.domain.Sort.unsorted()
                : org.springframework.data.domain.Sort.by("employeeCode").ascending();
        Pageable p = PageableDefaults.apply(pageable, 25, 200, defaultSort);
        return employees.list(status, employmentType,
                departmentId, sectionId, managerId, gradeId, designationId,
                hasEss, q, probationDueWithin, probationOverdue, includeTerminated, p);
    }

    @Operation(summary = "List an employee's direct reports",
            description = "Admin analogue of `/employees/me/team`. Paginated; default 25 rows. " +
                    "Optional `q` matches the same fields as `/employees` (fullName, employeeCode, " +
                    "workEmail, phone, cnic — case-insensitive substring + pg_trgm fuzzy on name/code). " +
                    "Each row carries the standard read enrichments (profilePictureUrl, directReportsCount). " +
                    "Requires `employee.read`.")
    @ApiResponse(responseCode = "404", description = "Employee not found.")
    @GetMapping("/{id}/team")
    @RequirePermission("employee.read")
    public com.forward.hrms.commons.web.page.PageResponse<EmployeeDto> team(
            @Parameter(description = "Manager employee id.") @PathVariable long id,
            @Parameter(description = "Case-insensitive substring search across fullName, employeeCode, " +
                    "workEmail, phone and cnic.")
            @RequestParam(required = false) String q,
            @Parameter(hidden = true) Pageable pageable) {
        Pageable p = PageableDefaults.apply(pageable, 25, 200,
                org.springframework.data.domain.Sort.by("employeeCode").ascending());
        return employees.team(id, q, p);
    }

    @Operation(summary = "List the employee's contract history",
            description = "Most-recent first. The current ACTIVE contract sits at the top. Requires `employee.read`.")
    @GetMapping("/{id}/contracts")
    @RequirePermission("employee.read")
    public java.util.List<EmploymentContractDto> listContracts(@PathVariable long id) {
        return contracts.list(id);
    }

    @Operation(summary = "Confirm an employee out of probation",
            description = "Allowed only while a PROBATION contract is ACTIVE. Marks it EXPIRED, " +
                    "inserts a new PERMANENT contract starting today, and sets confirmation_date. Requires `employee.write`.")
    @ApiResponse(responseCode = "409", description = "No active probation contract.")
    @PostMapping("/{id}/confirm")
    @RequirePermission("employee.write")
    public EmployeeDto confirm(@PathVariable long id, @Valid @RequestBody ConfirmEmployeeRequest req) {
        return contracts.confirm(id, req);
    }

    @Operation(summary = "Extend probation (one-time)",
            description = "Bumps the same PROBATION row's end_date to start_date + newProbationDays. " +
                    "Allowed values: 120 or 180; must be greater than current probation_days; " +
                    "and the contract's is_extended flag must still be false. Requires `employee.write`.")
    @ApiResponse(responseCode = "409", description = "Probation already extended.")
    @ApiResponse(responseCode = "422", description = "Invalid days.")
    @PostMapping("/{id}/extend-probation")
    @RequirePermission("employee.write")
    public EmployeeDto extendProbation(@PathVariable long id,
                                       @Valid @RequestBody ExtendProbationRequest req) {
        return contracts.extendProbation(id, req);
    }

    @Operation(summary = "Patch an employee", description = "HR-managed fields. Null fields are left unchanged. Requires `employee.write`.")
    @PatchMapping("/{id}")
    @RequirePermission("employee.write")
    public EmployeeDto update(@PathVariable long id, @Valid @RequestBody UpdateEmployeeRequest req) {
        return employees.update(id, req);
    }

    @Operation(summary = "Change manager (cycle-checked)", description = "Requires `employee.write`.")
    @PostMapping("/{id}/manager")
    @RequirePermission("employee.write")
    public EmployeeDto changeManager(@PathVariable long id, @RequestBody AssignmentChangeRequest req) {
        return employees.changeManager(id, req);
    }

    @Operation(summary = "Change department",
            description = "Clears the section assignment if it doesn't belong to the new department. Requires `employee.write`.")
    @PostMapping("/{id}/department")
    @RequirePermission("employee.write")
    public EmployeeDto changeDepartment(@PathVariable long id, @RequestBody AssignmentChangeRequest req) {
        return employees.changeDepartment(id, req);
    }

    @Operation(summary = "Change section",
            description = "Section must belong to the employee's current department. Requires `employee.write`.")
    @PostMapping("/{id}/section")
    @RequirePermission("employee.write")
    public EmployeeDto changeSection(@PathVariable long id, @RequestBody AssignmentChangeRequest req) {
        return employees.changeSection(id, req);
    }

    @Operation(summary = "Change designation", description = "Requires `employee.write`.")
    @PostMapping("/{id}/designation")
    @RequirePermission("employee.write")
    public EmployeeDto changeDesignation(@PathVariable long id, @RequestBody AssignmentChangeRequest req) {
        return employees.changeDesignation(id, req);
    }

    @Operation(summary = "Change grade", description = "Requires `employee.write`.")
    @PostMapping("/{id}/grade")
    @RequirePermission("employee.write")
    public EmployeeDto changeGrade(@PathVariable long id, @RequestBody AssignmentChangeRequest req) {
        return employees.changeGrade(id, req);
    }

    @Operation(summary = "Org-assignment history (paginated)",
            description = "Most recent first. Each row is `effective_from`..`effective_to` (null = current). Requires `employee.read`.")
    @GetMapping("/{id}/history")
    @RequirePermission("employee.read")
    public PageResponse<EmployeeHistoryDto> history(@PathVariable long id,
                                                    @Parameter(hidden = true) Pageable pageable) {
        return employees.history(id, pageable);
    }

    @Operation(summary = "Grant ESS to an existing employee",
            description = "Registers an ESS_GRANT saga whose PROVISION_ESS step calls auth-service "
                    + "to create the user and patches `employees.user_id`. Returns the saga id "
                    + "alongside the employee row; the FE polls `GET /sagas/{id}` for "
                    + "RUNNING / COMPLETED / FAILED. The previous in-tx HTTP call risked an "
                    + "orphaned auth user on a kickoff-tx failure; the saga's idempotency-key "
                    + "(`ess-grant:emp:{id}`) makes retries safe. Requires `employee.admin`.")
    @ApiResponse(responseCode = "409", description = "Employee already has ESS.")
    @PostMapping("/{id}/grant-ess")
    @RequirePermission("employee.admin")
    public GrantEssResponse grantEss(@PathVariable long id, @Valid @RequestBody GrantEssRequest req) {
        return ess.grant(id, req);
    }

    @Operation(summary = "Revoke ESS",
            description = "Emits `employee.ess_revoked`; auth-service disables the user and revokes sessions. Requires `employee.admin`.")
    @ApiResponse(responseCode = "409", description = "Employee has no ESS.")
    @PostMapping("/{id}/revoke-ess")
    @RequirePermission("employee.admin")
    public ResponseEntity<EmployeeDto> revokeEss(@PathVariable long id) {
        return ResponseEntity.ok(ess.revoke(id));
    }

    @Operation(summary = "Annual Leave Eligible employee",
            description = "Get list of eligible employee for annual leave calculation for month and year.")
    @PostMapping("/annual-leave/eligible")
    public ResponseEntity<List<AnnualLeaveEligibleEmployee>> annualLeaveEligibleEmployeeList(
            @RequestParam int year,
            @RequestParam int month) {

        return ResponseEntity.ok(
                ess.annualLeaveEligibleEmployeeList(year, month)
        );
    }
}
