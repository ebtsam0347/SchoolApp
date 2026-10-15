package com.forward.hrms.attendance.controller;

import com.forward.hrms.attendance.client.EmployeeClient;
import com.forward.hrms.attendance.dto.request.ApproveRequest;
import com.forward.hrms.attendance.dto.request.RegularizationRequestDto;
import com.forward.hrms.attendance.dto.request.RejectRequest;
import com.forward.hrms.attendance.service.CallerContext;
import com.forward.hrms.attendance.service.RegularizationService;
import com.forward.hrms.commons.web.security.RequirePermission;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Regularization",
        description = "Formal employee-driven flow for missed check-ins / check-outs. Final-stage " +
                "approval writes punches with source=REGULARIZED and triggers re-derivation of the day's row. " +
                "Requires `attendance.read` / `attendance.write`.")
@RestController
@RequestMapping("/regularizations")
@RequiredArgsConstructor
public class RegularizationController {

    private final RegularizationService regularizations;
    private final CallerContext caller;

    @Operation(summary = "Submit a regularization",
            description = "Self-submit by default. A manager may set `onBehalfOf` to submit for a " +
                    "direct report — the MANAGER stage is auto-approved and the request advances to " +
                    "HR. HR with `attendance.approval.override` may set `onBehalfOf` for any " +
                    "employee, in which case the whole chain is auto-approved (punches written, day " +
                    "recomputed). A holder of `team.request.generation.regularization` may set " +
                    "`onBehalfOf` for anyone in their own department — nothing is auto-approved, " +
                    "and the request opens at the *caller's* own manager, not the target's. " +
                    "Requires `attendance.write`.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
            description = "ON_BEHALF_NOT_AUTHORIZED — `onBehalfOf` set but caller is neither the " +
                    "target's manager, a same-department " +
                    "`team.request.generation.regularization` holder, or an override holder.")
    @PostMapping
    @RequirePermission("attendance.write")
    @ResponseStatus(HttpStatus.CREATED)
    public com.forward.hrms.attendance.dto.response.RegularizationRequestDto submit(
            @Valid @RequestBody RegularizationRequestDto body) {
        EmployeeClient.EmployeeView me = caller.requireEmployee();
        return regularizations.submit(me.id(), body);
    }

    @Operation(summary = "Pending approvals for the caller",
            description = "Paginated. Date filter on `attendanceDate`. Default sort newest-first. `q` matches " +
                    "the request id (exact, when numeric), or a case-insensitive substring of the request " +
                    "code, employee code, or employee name. Requires `attendance.read`.")
    @GetMapping("/pending-approvals")
    @RequirePermission("attendance.read")
    public com.forward.hrms.commons.web.page.PageResponse<
            com.forward.hrms.attendance.dto.response.RegularizationRequestDto> pending(
            @org.springframework.web.bind.annotation.RequestParam(required = false) java.time.LocalDate from,
            @org.springframework.web.bind.annotation.RequestParam(required = false) java.time.LocalDate to,
            @io.swagger.v3.oas.annotations.Parameter(description = "Request id, request code, employee code, or employee name.")
            @org.springframework.web.bind.annotation.RequestParam(required = false) String q,
            @io.swagger.v3.oas.annotations.Parameter(hidden = true)
            org.springframework.data.domain.Pageable pageable) {
        return regularizations.pendingApprovals(from, to, q,
                com.forward.hrms.commons.web.page.PageableDefaults.apply(pageable, "attendanceDate"));
    }

    @Operation(summary = "My department's regularizations",
            description = "Paginated list of every regularization in the caller's own department, " +
                    "whoever filed it (the employee, their manager, or a team-request generator), " +
                    "with its approval `steps` / `currentStage` and the target's `employee` " +
                    "summary. Optional `status`, `filedFrom` / `filedTo` (inclusive dates, on " +
                    "when it was filed) and `employeeId` (a colleague; outside the department -> " +
                    "empty). Default sort `id,desc`, page size 20 (max 200). Requires " +
                    "`team.request.generation.regularization`.")
    @GetMapping("/department")
    @RequirePermission("team.request.generation.regularization")
    public com.forward.hrms.commons.web.page.PageResponse<
            com.forward.hrms.attendance.dto.response.ApprovalsInbox.RegularizationRow> department(
            @org.springframework.web.bind.annotation.RequestParam(required = false)
            com.forward.hrms.attendance.enums.RegularizationStatus status,
            @org.springframework.web.bind.annotation.RequestParam(required = false) java.time.LocalDate filedFrom,
            @org.springframework.web.bind.annotation.RequestParam(required = false) java.time.LocalDate filedTo,
            @org.springframework.web.bind.annotation.RequestParam(required = false) Long employeeId,
            @io.swagger.v3.oas.annotations.Parameter(hidden = true)
            org.springframework.data.domain.Pageable pageable) {
        return regularizations.forDepartment(status, filedFrom, filedTo, employeeId,
                com.forward.hrms.commons.web.page.PageableDefaults.apply(pageable, "id"));
    }

    @Operation(summary = "Regularizations I filed for others",
            description = "Paginated list of regularizations the caller submitted on someone else's " +
                    "behalf (`onBehalfOf` — a manager for a direct report, or a team-request " +
                    "generator for a colleague). The caller's own self-submissions are excluded. " +
                    "Each row pairs the request with the target's `employee` summary. " +
                    "All filters optional; `from` / `to` filter on `attendanceDate`, `filedFrom` / " +
                    "`filedTo` (inclusive) on when it was filed. Default sort " +
                    "`attendanceDate,desc`. Default page size 20 (max 200). Requires `attendance.read`.")
    @GetMapping("/filed-by-me")
    @RequirePermission("attendance.read")
    public com.forward.hrms.commons.web.page.PageResponse<
            com.forward.hrms.attendance.dto.response.ApprovalsInbox.RegularizationRow> filedByMe(
            @org.springframework.web.bind.annotation.RequestParam(required = false)
            com.forward.hrms.attendance.enums.RegularizationStatus status,
            @org.springframework.web.bind.annotation.RequestParam(required = false) java.time.LocalDate from,
            @org.springframework.web.bind.annotation.RequestParam(required = false) java.time.LocalDate to,
            @org.springframework.web.bind.annotation.RequestParam(required = false) java.time.LocalDate filedFrom,
            @org.springframework.web.bind.annotation.RequestParam(required = false) java.time.LocalDate filedTo,
            @io.swagger.v3.oas.annotations.Parameter(hidden = true)
            org.springframework.data.domain.Pageable pageable) {
        return regularizations.filedOnBehalf(status, from, to, filedFrom, filedTo,
                com.forward.hrms.commons.web.page.PageableDefaults.apply(pageable, "attendanceDate"));
    }

    @Operation(summary = "Cancel my regularization",
            description = "Submitter-only. Allowed only while the request is PENDING. Requires `attendance.write`.")
    @PostMapping("/{id}/cancel")
    @RequirePermission("attendance.write")
    public com.forward.hrms.attendance.dto.response.RegularizationRequestDto cancel(@PathVariable long id) {
        return regularizations.cancel(id);
    }

    @Operation(summary = "Approve at caller's current stage",
            description = "Requires `attendance.write`.")
    @PostMapping("/{id}/approve")
    @RequirePermission("attendance.write")
    public com.forward.hrms.attendance.dto.response.RegularizationRequestDto approve(
            @PathVariable long id, @RequestBody(required = false) ApproveRequest body) {
        return regularizations.approve(id, body);
    }

    @Operation(summary = "Reject at caller's current stage",
            description = "Requires `attendance.write`.")
    @PostMapping("/{id}/reject")
    @RequirePermission("attendance.write")
    public com.forward.hrms.attendance.dto.response.RegularizationRequestDto reject(
            @PathVariable long id, @Valid @RequestBody RejectRequest body) {
        return regularizations.reject(id, body);
    }
}
