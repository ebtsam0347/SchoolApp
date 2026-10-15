package com.forward.hrms.attendance.controller;

import com.forward.hrms.attendance.client.EmployeeClient;
import com.forward.hrms.attendance.dto.request.ApproveRequest;
import com.forward.hrms.attendance.dto.request.CreateShortLeaveRequest;
import com.forward.hrms.attendance.dto.request.RejectRequest;
import com.forward.hrms.attendance.dto.response.ShortLeaveRequestDto;
import com.forward.hrms.attendance.enums.ShortLeaveStatus;
import com.forward.hrms.attendance.service.CallerContext;
import com.forward.hrms.attendance.service.ShortLeaveService;
import com.forward.hrms.commons.web.page.PageableDefaults;
import com.forward.hrms.commons.web.security.RequirePermission;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Short leaves",
        description = "Sub-day leave: leave work between (startTime, endTime) on a given date with HR " +
                "approval. Distinct from regularization (forgot-to-punch) and from full/half-day " +
                "leave_requests. Requires `attendance.read` / `attendance.write`.")
@RestController
@RequestMapping("/short-leaves")
@RequiredArgsConstructor
public class ShortLeaveController {

    private final ShortLeaveService shortLeaves;
    private final CallerContext caller;

    @Operation(summary = "Apply for a short leave",
            description = "Self-apply by default. A manager may set `onBehalfOf` to submit for a " +
                    "direct report — the MANAGER stage is auto-approved and the request advances to " +
                    "HR. HR with `attendance.approval.override` may set `onBehalfOf` for any " +
                    "employee, in which case the whole chain is auto-approved. A holder of " +
                    "`team.request.generation.shortLeave` may set `onBehalfOf` for anyone in their " +
                    "own department — nothing is auto-approved, and the request opens at the " +
                    "*caller's* own manager, not the target's. Requires `attendance.write`.")
    @ApiResponse(responseCode = "201", description = "Created and routed to first stage.")
    @ApiResponse(responseCode = "403", description = "ON_BEHALF_NOT_AUTHORIZED — `onBehalfOf` set " +
            "but caller is not the target's manager, a same-department " +
            "`team.request.generation.shortLeave` holder, or an override holder.")
    @ApiResponse(responseCode = "409", description = "SHORT_LEAVE_OVERLAPS_EXISTING — an active short leave " +
            "overlaps the requested window.")
    @ApiResponse(responseCode = "422", description = "SHORT_LEAVE_RANGE_INVALID — endTime is not after startTime.")
    @PostMapping
    @RequirePermission("attendance.write")
    @ResponseStatus(HttpStatus.CREATED)
    public ShortLeaveRequestDto apply(@Valid @RequestBody CreateShortLeaveRequest req) {
        EmployeeClient.EmployeeView me = caller.requireEmployee();
        return shortLeaves.submit(me.id(), req);
    }

    @Operation(summary = "My short leaves",
            description = "Paginated list of the caller's short leaves. All filters optional. " +
                    "Default sort `leaveDate,desc`. Default page size 20 (max 200). Requires `attendance.read`.")
    @GetMapping("/me")
    @RequirePermission("attendance.read")
    public com.forward.hrms.commons.web.page.PageResponse<ShortLeaveRequestDto> mine(
            @RequestParam(required = false) ShortLeaveStatus status,
            @RequestParam(required = false) java.time.LocalDate from,
            @RequestParam(required = false) java.time.LocalDate to,
            @io.swagger.v3.oas.annotations.Parameter(hidden = true)
            org.springframework.data.domain.Pageable pageable) {
        EmployeeClient.EmployeeView me = caller.requireEmployee();
        return shortLeaves.searchForEmployee(me.id(), status, from, to,
                PageableDefaults.apply(pageable, "leaveDate"));
    }

    @Operation(summary = "My team's short leaves",
            description = "Paginated list of short leaves belonging to the caller's direct reports, " +
                    "whoever submitted them (the report, or the manager on their behalf). Empty for a " +
                    "caller who manages nobody. Same filters as `/short-leaves/me`, plus optional " +
                    "`employeeId` (one direct report; anyone else -> empty). Default sort " +
                    "`leaveDate,desc`. Default page size 20 (max 200). Requires `attendance.read`.")
    @GetMapping("/team")
    @RequirePermission("attendance.read")
    public com.forward.hrms.commons.web.page.PageResponse<ShortLeaveRequestDto> team(
            @RequestParam(required = false) ShortLeaveStatus status,
            @RequestParam(required = false) java.time.LocalDate from,
            @RequestParam(required = false) java.time.LocalDate to,
            @RequestParam(required = false) Long employeeId,
            @io.swagger.v3.oas.annotations.Parameter(hidden = true)
            org.springframework.data.domain.Pageable pageable) {
        return shortLeaves.searchForTeam(status, from, to, employeeId,
                PageableDefaults.apply(pageable, "leaveDate"));
    }

    @Operation(summary = "My department's short leaves",
            description = "Paginated list of every short leave in the caller's own department, " +
                    "whoever filed it (the employee, their manager, or a team-request generator), " +
                    "with its approval `steps` / `currentStage` and the target's `employee` " +
                    "summary. Optional `status`, `filedFrom` / `filedTo` (inclusive dates, on " +
                    "when it was filed) and `employeeId` (a colleague; outside the department -> " +
                    "empty). Default sort `id,desc`, page size 20 (max 200). Requires " +
                    "`team.request.generation.shortLeave`, or a manager (MANAGER role, or someone " +
                    "with direct reports) holding `attendance.read`.")
    @GetMapping("/department")
    @RequirePermission({"team.request.generation.shortLeave", "attendance.read"})
    public com.forward.hrms.commons.web.page.PageResponse<
            com.forward.hrms.attendance.dto.response.ApprovalsInbox.ShortLeaveRow> department(
            @org.springframework.web.bind.annotation.RequestParam(required = false)
            com.forward.hrms.attendance.enums.ShortLeaveStatus status,
            @org.springframework.web.bind.annotation.RequestParam(required = false) java.time.LocalDate filedFrom,
            @org.springframework.web.bind.annotation.RequestParam(required = false) java.time.LocalDate filedTo,
            @org.springframework.web.bind.annotation.RequestParam(required = false) Long employeeId,
            @io.swagger.v3.oas.annotations.Parameter(hidden = true)
            org.springframework.data.domain.Pageable pageable) {
        return shortLeaves.forDepartment(status, filedFrom, filedTo, employeeId,
                com.forward.hrms.commons.web.page.PageableDefaults.apply(pageable, "id"));
    }

    @Operation(summary = "Short leaves I filed for others",
            description = "Paginated list of short leaves the caller submitted on someone else's " +
                    "behalf (`onBehalfOf` — a manager for a direct report, or a team-request " +
                    "generator for a colleague). The caller's own self-submissions are excluded. " +
                    "Each row pairs the request (with its approval `steps` / `currentStage`) with " +
                    "the target's `employee` summary. Default sort `id,desc`, page size 20 " +
                    "(max 200). Optional `filedFrom` / `filedTo` (inclusive dates) filter on when it was " +
                    "filed. Requires `attendance.read`.")
    @GetMapping("/filed-by-me")
    @RequirePermission("attendance.read")
    public com.forward.hrms.commons.web.page.PageResponse<
            com.forward.hrms.attendance.dto.response.ApprovalsInbox.ShortLeaveRow> filedByMe(
            @org.springframework.web.bind.annotation.RequestParam(required = false)
            com.forward.hrms.attendance.enums.ShortLeaveStatus status,
            @org.springframework.web.bind.annotation.RequestParam(required = false) java.time.LocalDate filedFrom,
            @org.springframework.web.bind.annotation.RequestParam(required = false) java.time.LocalDate filedTo,
            @io.swagger.v3.oas.annotations.Parameter(hidden = true)
            org.springframework.data.domain.Pageable pageable) {
        return shortLeaves.filedOnBehalf(status, filedFrom, filedTo,
                com.forward.hrms.commons.web.page.PageableDefaults.apply(pageable, "id"));
    }

    @Operation(summary = "Pending approvals — caller's stage-aware inbox",
            description = "Paginated. Date filter on `leaveDate`. Default sort newest-first. `q` matches " +
                    "the request id (exact, when numeric), or a case-insensitive substring of the request " +
                    "code, employee code, or employee name. Requires `attendance.read`.")
    @GetMapping("/pending-approvals")
    @RequirePermission("attendance.read")
    public com.forward.hrms.commons.web.page.PageResponse<ShortLeaveRequestDto> pending(
            @RequestParam(required = false) java.time.LocalDate from,
            @RequestParam(required = false) java.time.LocalDate to,
            @io.swagger.v3.oas.annotations.Parameter(description = "Request id, request code, employee code, or employee name.")
            @RequestParam(required = false) String q,
            @io.swagger.v3.oas.annotations.Parameter(hidden = true)
            org.springframework.data.domain.Pageable pageable) {
        return shortLeaves.pendingApprovals(from, to, q, pageable);
    }

    @Operation(summary = "Get a short leave",
            description = "Requires `attendance.read`.")
    @GetMapping("/{id}")
    @RequirePermission("attendance.read")
    public ShortLeaveRequestDto get(@PathVariable long id) {
        return shortLeaves.get(id);
    }

    @Operation(summary = "Approve at the caller's current stage",
            description = "Requires `attendance.write`.")
    @PostMapping("/{id}/approve")
    @RequirePermission("attendance.write")
    public ShortLeaveRequestDto approve(@PathVariable long id, @RequestBody(required = false) ApproveRequest body) {
        return shortLeaves.approve(id, body);
    }

    @Operation(summary = "Reject at the caller's current stage",
            description = "Requires `attendance.write`.")
    @PostMapping("/{id}/reject")
    @RequirePermission("attendance.write")
    public ShortLeaveRequestDto reject(@PathVariable long id, @Valid @RequestBody RejectRequest body) {
        return shortLeaves.reject(id, body);
    }

    @Operation(summary = "Cancel my short leave",
            description = "Submitter-only. PENDING any time, or APPROVED before the leave date. Requires `attendance.write`.")
    @ApiResponse(responseCode = "403", description = "Caller is not the submitter.")
    @ApiResponse(responseCode = "409", description = "SHORT_LEAVE_NOT_CANCELLABLE — not in a cancellable state.")
    @PostMapping("/{id}/cancel")
    @RequirePermission("attendance.write")
    public ShortLeaveRequestDto cancel(@PathVariable long id) {
        return shortLeaves.cancel(id);
    }
}
