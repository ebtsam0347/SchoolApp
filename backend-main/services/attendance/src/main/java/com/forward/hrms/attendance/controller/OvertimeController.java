package com.forward.hrms.attendance.controller;

import com.forward.hrms.attendance.client.EmployeeClient;
import com.forward.hrms.attendance.dto.request.ApproveRequest;
import com.forward.hrms.attendance.dto.request.BulkOvertimeRequest;
import com.forward.hrms.attendance.dto.request.CreateOvertimeRequest;
import com.forward.hrms.attendance.dto.request.RejectRequest;
import com.forward.hrms.attendance.dto.response.BulkOvertimeResponse;
import com.forward.hrms.attendance.dto.response.OvertimeRequestDto;
import com.forward.hrms.attendance.service.CallerContext;
import com.forward.hrms.attendance.service.OvertimeService;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Overtime",
        description = "Overtime authorization. Self-apply, bulk apply for a department, batch approve / reject. " +
                "Requires `attendance.read` / `attendance.write` / `attendance.overtime.bulk`.")
@RestController
@RequestMapping("/overtime")
@RequiredArgsConstructor
public class OvertimeController {

    private final OvertimeService overtimes;
    private final CallerContext caller;
    private final com.forward.hrms.attendance.service.AttendanceQueryService attendanceQuery;

    @Operation(summary = "Apply for OT on a working day",
            description = "Self-apply by default. A manager may set `onBehalfOf` to submit for a " +
                    "direct report — the MANAGER stage is auto-approved and the request advances to " +
                    "HR. HR with `attendance.approval.override` may set `onBehalfOf` for any " +
                    "employee, in which case the whole chain is auto-approved. A holder of " +
                    "`team.request.generation.overtime` may set `onBehalfOf` for anyone in their " +
                    "own department — nothing is auto-approved, and the request opens at the " +
                    "*caller's* own manager, not the target's. Requires `attendance.write`.")
    @ApiResponse(responseCode = "403", description = "ON_BEHALF_NOT_AUTHORIZED — `onBehalfOf` set " +
            "but caller is not the target's manager, a same-department " +
            "`team.request.generation.overtime` holder, or an override holder.")
    @PostMapping
    @RequirePermission("attendance.write")
    @ResponseStatus(HttpStatus.CREATED)
    public OvertimeRequestDto apply(@Valid @RequestBody CreateOvertimeRequest req) {
        EmployeeClient.EmployeeView me = caller.requireEmployee();
        return overtimes.selfApply(me.id(), req);
    }

    @Operation(summary = "Bulk apply for a department",
            description = "All employees must be in the submitter's department; rows for employees " +
                    "without a punch on the date are returned in `skipped[]`. Requires `attendance.overtime.bulk`.")
    @PostMapping("/bulk")
    @RequirePermission("attendance.overtime.bulk")
    public BulkOvertimeResponse bulk(@Valid @RequestBody BulkOvertimeRequest req) {
        return overtimes.bulk(req);
    }

    @Operation(summary = "My OT requests",
            description = "Paginated list of the caller's OT requests. All filters optional. " +
                    "Default sort `attendanceDate,desc`. Default page size 20 (max 200). Requires `attendance.read`.")
    @GetMapping("/me")
    @RequirePermission("attendance.read")
    public com.forward.hrms.commons.web.page.PageResponse<OvertimeRequestDto> mine(
            @org.springframework.web.bind.annotation.RequestParam(required = false)
            com.forward.hrms.attendance.enums.OvertimeStatus status,
            @org.springframework.web.bind.annotation.RequestParam(required = false) java.time.LocalDate from,
            @org.springframework.web.bind.annotation.RequestParam(required = false) java.time.LocalDate to,
            @io.swagger.v3.oas.annotations.Parameter(hidden = true)
            org.springframework.data.domain.Pageable pageable) {
        EmployeeClient.EmployeeView me = caller.requireEmployee();
        return overtimes.searchForEmployee(me.id(), status, from, to,
                PageableDefaults.apply(pageable, "attendanceDate"));
    }

    @Operation(summary = "My team's OT requests",
            description = "Paginated list of overtime requests belonging to the caller's direct " +
                    "reports, whoever submitted them (the report, or the manager on their behalf), " +
                    "each paired with the target's `employee` summary. Empty for a caller who " +
                    "manages nobody. Same filters as `/overtime/me`, plus optional `employeeId` " +
                    "(one direct report; anyone else -> empty). Default sort `attendanceDate,desc`. " +
                    "Default page size 20 (max 200). Requires `attendance.read`.")
    @GetMapping("/team")
    @RequirePermission("attendance.read")
    public com.forward.hrms.commons.web.page.PageResponse<
            com.forward.hrms.attendance.dto.response.ApprovalsInbox.OvertimeRow> team(
            @org.springframework.web.bind.annotation.RequestParam(required = false)
            com.forward.hrms.attendance.enums.OvertimeStatus status,
            @org.springframework.web.bind.annotation.RequestParam(required = false) java.time.LocalDate from,
            @org.springframework.web.bind.annotation.RequestParam(required = false) java.time.LocalDate to,
            @org.springframework.web.bind.annotation.RequestParam(required = false) Long employeeId,
            @io.swagger.v3.oas.annotations.Parameter(hidden = true)
            org.springframework.data.domain.Pageable pageable) {
        return overtimes.searchForTeam(status, from, to, employeeId,
                PageableDefaults.apply(pageable, "attendanceDate"));
    }

    @Operation(summary = "My department's overtime requests",
            description = "Paginated list of every overtime request in the caller's own department, " +
                    "whoever filed it (the employee, their manager, or a team-request generator), " +
                    "with its approval `steps` / `currentStage` and the target's `employee` " +
                    "summary. Optional `status`, `filedFrom` / `filedTo` (inclusive dates, on " +
                    "when it was filed) and `employeeId` (a colleague; outside the department -> " +
                    "empty). Default sort `id,desc`, page size 20 (max 200). Requires " +
                    "`team.request.generation.overtime`, or a manager (MANAGER role, or someone " +
                    "with direct reports) holding `attendance.read`.")
    @GetMapping("/department")
    @RequirePermission({"team.request.generation.overtime", "attendance.read"})
    public com.forward.hrms.commons.web.page.PageResponse<
            com.forward.hrms.attendance.dto.response.ApprovalsInbox.OvertimeRow> department(
            @org.springframework.web.bind.annotation.RequestParam(required = false)
            com.forward.hrms.attendance.enums.OvertimeStatus status,
            @org.springframework.web.bind.annotation.RequestParam(required = false) java.time.LocalDate filedFrom,
            @org.springframework.web.bind.annotation.RequestParam(required = false) java.time.LocalDate filedTo,
            @org.springframework.web.bind.annotation.RequestParam(required = false) Long employeeId,
            @io.swagger.v3.oas.annotations.Parameter(hidden = true)
            org.springframework.data.domain.Pageable pageable) {
        return overtimes.forDepartment(status, filedFrom, filedTo, employeeId,
                com.forward.hrms.commons.web.page.PageableDefaults.apply(pageable, "id"));
    }

    @Operation(summary = "My department's attendance on a date",
            description = "Roll-call for bulk OT on the Team requests page, paginated: employees of " +
                    "the caller's own department (themselves included) with status, punch in/out " +
                    "and lateness for `date`, or `PENDING_PUNCH` when there is no record. Rows match " +
                    "`/bff/attendance/team/today`. `date` (YYYY-MM-DD) is required. Optional " +
                    "filters combine: `sectionId` (one section; outside the department -> empty), " +
                    "`search` (case-insensitive substring of full name, employee code, work email, " +
                    "phone or CNIC) and " +
                    "`status` (an AttendanceStatus — PRESENT, ABSENT, LEAVE, HOLIDAY, WEEKEND, " +
                    "HALF_DAY; employees with no record that day are PENDING_PUNCH and only " +
                    "appear unfiltered). Sorted by employee code; page size 20 (max 200). Requires " +
                    "`team.request.generation.overtime`.")
    @GetMapping("/department/attendance")
    @RequirePermission("team.request.generation.overtime")
    public com.forward.hrms.commons.web.page.PageResponse<
            com.forward.hrms.attendance.dto.response.DepartmentAttendanceRow> departmentAttendance(
            @org.springframework.web.bind.annotation.RequestParam
            @org.springframework.format.annotation.DateTimeFormat(
                    iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            java.time.LocalDate date,
            @org.springframework.web.bind.annotation.RequestParam(required = false) Long sectionId,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String search,
            @org.springframework.web.bind.annotation.RequestParam(required = false)
            com.forward.hrms.attendance.enums.AttendanceStatus status,
            @io.swagger.v3.oas.annotations.Parameter(hidden = true)
            org.springframework.data.domain.Pageable pageable) {
        return attendanceQuery.departmentDay(date, sectionId, search, status,
                PageableDefaults.apply(pageable, PageableDefaults.DEFAULT_SIZE,
                        PageableDefaults.MAX_SIZE,
                        org.springframework.data.domain.Sort.by("employeeCode").ascending()));
    }

    @Operation(summary = "Overtime requests I filed for others",
            description = "Paginated list of overtime requests the caller submitted on someone else's " +
                    "behalf (`onBehalfOf` — a manager for a direct report, or a team-request " +
                    "generator for a colleague). The caller's own self-submissions are excluded. " +
                    "Each row pairs the request (with its approval `steps` / `currentStage`) with " +
                    "the target's `employee` summary. Default sort `id,desc`, page size 20 " +
                    "(max 200). Optional `filedFrom` / `filedTo` (inclusive dates) filter on when it was " +
                    "filed. Requires `attendance.read`.")
    @GetMapping("/filed-by-me")
    @RequirePermission("attendance.read")
    public com.forward.hrms.commons.web.page.PageResponse<
            com.forward.hrms.attendance.dto.response.ApprovalsInbox.OvertimeRow> filedByMe(
            @org.springframework.web.bind.annotation.RequestParam(required = false)
            com.forward.hrms.attendance.enums.OvertimeStatus status,
            @org.springframework.web.bind.annotation.RequestParam(required = false) java.time.LocalDate filedFrom,
            @org.springframework.web.bind.annotation.RequestParam(required = false) java.time.LocalDate filedTo,
            @io.swagger.v3.oas.annotations.Parameter(hidden = true)
            org.springframework.data.domain.Pageable pageable) {
        return overtimes.filedOnBehalf(status, filedFrom, filedTo,
                com.forward.hrms.commons.web.page.PageableDefaults.apply(pageable, "id"));
    }

    @Operation(summary = "Get one OT request",
            description = "A single overtime request with its approval `steps` / `currentStage`, " +
                    "like `GET /leaves/{id}`. 404 OVERTIME_NOT_FOUND when it doesn't exist. " +
                    "Requires `attendance.read`.")
    @GetMapping("/{id:\\d+}")
    @RequirePermission("attendance.read")
    public OvertimeRequestDto get(@PathVariable long id) {
        return overtimes.get(id);
    }

    @Operation(summary = "Pending OT approvals — caller's stage-aware inbox",
            description = "Paginated. Date filter on `attendanceDate`. Default sort newest-first. `q` matches " +
                    "the request id (exact, when numeric), or a case-insensitive substring of the request " +
                    "code, employee code, or employee name. `overtimeType` is exact. Requires `attendance.read`.")
    @GetMapping("/pending-approvals")
    @RequirePermission("attendance.read")
    public com.forward.hrms.commons.web.page.PageResponse<OvertimeRequestDto> pending(
            @org.springframework.web.bind.annotation.RequestParam(required = false) java.time.LocalDate from,
            @org.springframework.web.bind.annotation.RequestParam(required = false) java.time.LocalDate to,
            @io.swagger.v3.oas.annotations.Parameter(description = "Request id, request code, employee code, or employee name.")
            @org.springframework.web.bind.annotation.RequestParam(required = false) String q,
            @org.springframework.web.bind.annotation.RequestParam(required = false)
            com.forward.hrms.attendance.enums.OvertimeType overtimeType,
            @io.swagger.v3.oas.annotations.Parameter(hidden = true)
            org.springframework.data.domain.Pageable pageable) {
        return overtimes.pendingApprovals(from, to, q, overtimeType, pageable);
    }

    @Operation(summary = "Cancel my OT request",
            description = "Submitter-only. PENDING any time, or APPROVED before the work date. " +
                    "If the request was APPROVED the day's OT minute buckets are recomputed. Requires `attendance.write`.")
    @ApiResponse(responseCode = "200", description = "Cancelled.")
    @ApiResponse(responseCode = "403", description = "Caller is not the submitter.")
    @ApiResponse(responseCode = "409", description = "Request not in a cancellable state.")
    @PostMapping("/{id}/cancel")
    @RequirePermission("attendance.write")
    public OvertimeRequestDto cancel(@PathVariable long id) {
        return overtimes.cancel(id);
    }

    @Operation(summary = "Approve an OT request at the caller's current stage",
            description = "Requires `attendance.write`.")
    @PostMapping("/{id}/approve")
    @RequirePermission("attendance.write")
    public OvertimeRequestDto approve(@PathVariable long id, @RequestBody(required = false) ApproveRequest body) {
        return overtimes.approve(id, body);
    }

    @Operation(summary = "Reject an OT request at the caller's current stage",
            description = "Requires `attendance.write`.")
    @PostMapping("/{id}/reject")
    @RequirePermission("attendance.write")
    public OvertimeRequestDto reject(@PathVariable long id, @Valid @RequestBody RejectRequest body) {
        return overtimes.reject(id, body);
    }

    @Operation(summary = "Approve every row in a bulk batch the caller can act on",
            description = "Requires `attendance.write`.")
    @PostMapping("/batches/{batchId}/approve")
    @RequirePermission("attendance.write")
    public List<OvertimeRequestDto> approveBatch(@PathVariable UUID batchId,
                                                 @RequestBody(required = false) ApproveRequest body) {
        return overtimes.approveBatch(batchId, body);
    }

    @Operation(summary = "Reject every row in a bulk batch",
            description = "Requires `attendance.write`.")
    @PostMapping("/batches/{batchId}/reject")
    @RequirePermission("attendance.write")
    public List<OvertimeRequestDto> rejectBatch(@PathVariable UUID batchId,
                                                @Valid @RequestBody RejectRequest body) {
        return overtimes.rejectBatch(batchId, body);
    }
}
