package com.forward.hrms.attendance.controller;

import com.forward.hrms.attendance.client.EmployeeClient;
import com.forward.hrms.attendance.dto.request.*;
import com.forward.hrms.attendance.dto.response.*;
import com.forward.hrms.attendance.entity.LeaveType;
import com.forward.hrms.attendance.enums.LeaveRequestStatus;
import com.forward.hrms.attendance.repository.LeaveTypeRepository;
import com.forward.hrms.attendance.service.CallerContext;
import com.forward.hrms.attendance.service.LeaveBalanceService;
import com.forward.hrms.attendance.service.LeaveService;
import com.forward.hrms.attendance.service.LeaveTypeAdminService;
import com.forward.hrms.attendance.service.PayslipAttendanceService;
import com.forward.hrms.commons.web.page.PageableDefaults;
import com.forward.hrms.commons.web.security.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "Leaves", description = "Leave requests, multi-stage approvals, balances, leave types. Requires `attendance.read` / `attendance.write`.")
@RestController
@RequestMapping("/leaves")
@RequiredArgsConstructor
public class LeaveController {

    private final LeaveService leaves;
    private final LeaveBalanceService balances;
    private final PayslipAttendanceService payslipAttendance;
    private final LeaveTypeRepository leaveTypes;
    private final LeaveTypeAdminService leaveTypeAdmin;
    private final CallerContext caller;

    @Operation(summary = "List leave types",
            description = "Active types only by default. Pass `includeInactive=true` for the HR-admin " +
                    "catalog UI (so deactivated types can be re-enabled). Requires `attendance.read`.")
    @GetMapping("/types")
    @RequirePermission("attendance.read")
    public List<LeaveTypeDto> listTypes(
            @RequestParam(required = false, defaultValue = "false") boolean includeInactive) {
        List<LeaveType> rows = includeInactive
                ? leaveTypes.findAllByOrderByCodeAsc()
                : leaveTypes.findAllByActiveTrueOrderByCodeAsc();
        return rows.stream().map(LeaveTypeDtoMapper::of).toList();
    }

    @Operation(summary = "Create leave type",
            description = "HR-admin. `code` is unique and immutable post-create. Requires `attendance.admin`.")
    @ApiResponse(responseCode = "201", description = "Created.")
    @ApiResponse(responseCode = "409", description = "LEAVE_TYPE_CODE_TAKEN — a type with this code already exists.")
    @PostMapping("/types")
    @RequirePermission("attendance.admin")
    @ResponseStatus(HttpStatus.CREATED)
    public LeaveTypeDto createType(@Valid @RequestBody CreateLeaveTypeRequest req) {
        return LeaveTypeDtoMapper.of(leaveTypeAdmin.create(req));
    }

    @Operation(summary = "Update leave type",
            description = "HR-admin partial update. `code` is not updatable. Requires `attendance.admin`.")
    @ApiResponse(responseCode = "404", description = "LEAVE_TYPE_NOT_FOUND.")
    @PatchMapping("/types/{id}")
    @RequirePermission("attendance.admin")
    public LeaveTypeDto updateType(@PathVariable long id, @Valid @RequestBody UpdateLeaveTypeRequest req) {
        return LeaveTypeDtoMapper.of(leaveTypeAdmin.update(id, req));
    }

    @Operation(summary = "Deactivate leave type",
            description = "Soft delete — flips `active=false`. Existing balance rows and historical " +
                    "leave requests are preserved; ESS callers stop seeing the type on the apply " +
                    "drawer and dashboard. Re-enable by PATCHing `active=true`. Requires `attendance.admin`.")
    @ApiResponse(responseCode = "404", description = "LEAVE_TYPE_NOT_FOUND.")
    @DeleteMapping("/types/{id}")
    @RequirePermission("attendance.admin")
    public LeaveTypeDto deactivateType(@PathVariable long id) {
        return LeaveTypeDtoMapper.of(leaveTypeAdmin.deactivate(id));
    }

    @Operation(summary = "My leave balances",
            description = "Without `year`, returns the currently active cycle per leave type " +
                    "(the cycle window containing today — correct for annual anniversary cycles and " +
                    "Jul–Jun fiscal cycles alike). Pass `year` for a historical view: cycles that " +
                    "started in that calendar year. Requires `attendance.read`.")
    @GetMapping("/me/balances")
    @RequirePermission("attendance.read")
    public List<LeaveBalanceDto> myBalances(
            @RequestParam(required = false) Integer year) {
        EmployeeClient.EmployeeView me = caller.requireEmployee();
        return balances.myBalances(me.id(), year);
    }

    @Operation(summary = "A department colleague's leave balances",
            description = "Counterpart of `/leaves/me/balances` for the team-request-generation " +
                    "tab: the balances of the employee a request is about to be raised for. " +
                    "Same `year` semantics — without it, the currently active cycle per leave " +
                    "type; with it, cycles that started in that calendar year — and it returns " +
                    "the same list that employee sees for themselves. The target must be in the " +
                    "caller's own department. Requires `attendance.read`.")
    @ApiResponse(responseCode = "403", description = "ON_BEHALF_NOT_AUTHORIZED — the employee is " +
            "not in the caller's department (also returned for an unknown employee id).")
    @GetMapping("/employees/{employeeId}/balances")
    @RequirePermission("attendance.read")
    public List<LeaveBalanceDto> balancesForEmployee(
            @Parameter(description = "Employee the balances are for; must share the caller's department.")
            @PathVariable long employeeId,
            @Parameter(description = "Historical view: cycles that started in this calendar year.")
            @RequestParam(required = false) Integer year) {
        EmployeeClient.EmployeeView me = caller.requireEmployee();
        return leaves.balancesForDepartmentEmployee(me.id(), employeeId, year);
    }

    // new endpoint for not just annual leaves balance on termination date of employee.
    @GetMapping("/employee/leaves-termination-summary/{employeeId}")
    @RequirePermission("attendance.read")
    public TerminationLeavesSummaryDto terminationLeavesSummary(
            @PathVariable long employeeId,
            @RequestParam LocalDate lastWorkingDay) {

        return balances.terminationLeavesSummary(employeeId, lastWorkingDay);
    }

    @Operation(summary = "Payslip attendance + leave inputs for a page of employees",
            description = "For each employee id (cap 200): the derived per-day attendance " +
                    "classification for [periodStart, periodEnd] (Sunday off, public holidays, " +
                    "approved leave, presence, absence, joining date) used to validate what " +
                    "payroll persisted, every paid leave type's usage this month and this leave " +
                    "cycle (from approved leave records), and annual leave's own block. One set of " +
                    "set-based queries serves the whole page. Requires `attendance.read`.")
    @GetMapping("/employees/payslip-summary")
    @RequirePermission("attendance.read")
    public List<EmployeeLeavePayslipSummaryDto> payslipSummaries(
            @RequestParam java.util.Set<Long> ids,
            @RequestParam LocalDate periodStart,
            @RequestParam LocalDate periodEnd) {
        if (ids.size() > 200) {
            throw new com.forward.hrms.commons.core.error.UnprocessableError(
                    com.forward.hrms.attendance.enums.AttendanceErrorCode.LEAVE_RANGE_INVALID,
                    "ids batch capped at 200; got " + ids.size());
        }
        return payslipAttendance.summaries(ids, periodStart, periodEnd);
    }

    @Operation(summary = "My leaves",
            description = "Paginated list of the caller's leave requests. All filters are " +
                    "optional. Date filter is overlap-style — include any request whose " +
                    "[fromDate, toDate] intersects [from, to]. Default sort is `fromDate,desc`. " +
                    "Default page size 20 (max 200). Requires `attendance.read`.")
    @GetMapping("/me")
    @RequirePermission("attendance.read")
    public com.forward.hrms.commons.web.page.PageResponse<LeaveRequestDto> myLeaves(
            @RequestParam(required = false) LeaveRequestStatus status,
            @RequestParam(required = false) Long leaveTypeId,
            @RequestParam(required = false) java.time.LocalDate from,
            @RequestParam(required = false) java.time.LocalDate to,
            @io.swagger.v3.oas.annotations.Parameter(hidden = true)
            org.springframework.data.domain.Pageable pageable) {
        EmployeeClient.EmployeeView me = caller.requireEmployee();
        return leaves.searchForEmployee(me.id(), status, leaveTypeId, from, to,
                PageableDefaults.apply(pageable, "fromDate"));
    }

    @Operation(summary = "Apply for leave",
            description = "Self-apply by default. A manager may set `onBehalfOf` to submit for a " +
                    "direct report — the MANAGER stage is auto-approved (the manager submitted, no " +
                    "re-approval needed) and the request advances to HR. HR with " +
                    "`attendance.approval.override` may set `onBehalfOf` for any employee, in which " +
                    "case the whole chain is auto-approved. A holder of " +
                    "`team.request.generation.leave` may set `onBehalfOf` for anyone in their own " +
                    "department — nothing is auto-approved, and the request opens at the " +
                    "*caller's* own manager, not the target's. Requires `attendance.write`.")
    @ApiResponse(responseCode = "201", description = "Leave created and routed to first pending stage.")
    @ApiResponse(responseCode = "403", description = "ON_BEHALF_NOT_AUTHORIZED — `onBehalfOf` set " +
            "but caller is not the target's manager, a same-department " +
            "`team.request.generation.leave` holder, or an override holder.")
    @PostMapping
    @RequirePermission("attendance.write")
    @ResponseStatus(HttpStatus.CREATED)
    public LeaveRequestDto apply(@Valid @RequestBody CreateLeaveRequest req) {
        EmployeeClient.EmployeeView me = caller.requireEmployee();
        return leaves.submit(me.id(), req);
    }

    @Operation(summary = "Preview a leave request",
            description = "Dry-run for the apply-leave drawer. Returns the projected workingDays, " +
                    "skip counts, balanceAfter, and any soft warnings the FE should surface " +
                    "before the user hits submit. Same business rules submit enforces. Requires `attendance.read`.")
    @ApiResponse(responseCode = "200", description = "Preview computed.")
    @PostMapping("/preview")
    @RequirePermission("attendance.read")
    public LeavePreviewDto preview(@Valid @RequestBody CreateLeaveRequest req) {
        EmployeeClient.EmployeeView me = caller.requireEmployee();
        return leaves.preview(me.id(), req);
    }

    @Operation(summary = "Cancel my leave",
            description = "Requires `attendance.write`.")
    @PostMapping("/{id}/cancel")
    @RequirePermission("attendance.write")
    public LeaveRequestDto cancel(@PathVariable long id) {
        return leaves.cancel(id);
    }

    @Operation(summary = "My team's leaves",
            description = "Paginated list of leaves belonging to the caller's direct reports, " +
                    "whoever submitted them (the report, or the manager on their behalf), each " +
                    "paired with the target's `employee` summary. Empty for a caller who manages " +
                    "nobody. Same filters as `/leaves/me` (overlap-style `from` / `to`), plus " +
                    "optional `employeeId` (one direct report; anyone else -> empty). Default " +
                    "sort `fromDate,desc`. Default page size 20 (max 200). Requires `attendance.read`.")
    @GetMapping("/team")
    @RequirePermission("attendance.read")
    public com.forward.hrms.commons.web.page.PageResponse<
            com.forward.hrms.attendance.dto.response.ApprovalsInbox.LeaveRow> teamLeaves(
            @RequestParam(required = false) LeaveRequestStatus status,
            @RequestParam(required = false) Long leaveTypeId,
            @RequestParam(required = false) java.time.LocalDate from,
            @RequestParam(required = false) java.time.LocalDate to,
            @RequestParam(required = false) Long employeeId,
            @io.swagger.v3.oas.annotations.Parameter(hidden = true)
            org.springframework.data.domain.Pageable pageable) {
        return leaves.searchForTeam(status, leaveTypeId, from, to, employeeId,
                PageableDefaults.apply(pageable, "fromDate"));
    }

    @Operation(summary = "My department's leaves",
            description = "Paginated list of every leave in the caller's own department, " +
                    "whoever filed it (the employee, their manager, or a team-request generator), " +
                    "with its approval `steps` / `currentStage` and the target's `employee` " +
                    "summary. Optional `status`, `filedFrom` / `filedTo` (inclusive dates, on " +
                    "when it was filed) and `employeeId` (a colleague; outside the department -> " +
                    "empty). Default sort `id,desc`, page size 20 (max 200). Requires " +
                    "`team.request.generation.leave`, or a manager (MANAGER role, or someone " +
                    "with direct reports) holding `attendance.read`.")
    @GetMapping("/department")
    @RequirePermission({"team.request.generation.leave", "attendance.read"})
    public com.forward.hrms.commons.web.page.PageResponse<
            com.forward.hrms.attendance.dto.response.ApprovalsInbox.LeaveRow> department(
            @org.springframework.web.bind.annotation.RequestParam(required = false)
            com.forward.hrms.attendance.enums.LeaveRequestStatus status,
            @org.springframework.web.bind.annotation.RequestParam(required = false) java.time.LocalDate filedFrom,
            @org.springframework.web.bind.annotation.RequestParam(required = false) java.time.LocalDate filedTo,
            @org.springframework.web.bind.annotation.RequestParam(required = false) Long employeeId,
            @io.swagger.v3.oas.annotations.Parameter(hidden = true)
            org.springframework.data.domain.Pageable pageable) {
        return leaves.forDepartment(status, filedFrom, filedTo, employeeId,
                com.forward.hrms.commons.web.page.PageableDefaults.apply(pageable, "id"));
    }

    @Operation(summary = "Leaves I filed for others",
            description = "Paginated list of leaves the caller submitted on someone else's " +
                    "behalf (`onBehalfOf` — a manager for a direct report, or a team-request " +
                    "generator for a colleague). The caller's own self-submissions are excluded. " +
                    "Each row pairs the request (with its approval `steps` / `currentStage`) with " +
                    "the target's `employee` summary. Default sort `id,desc`, page size 20 " +
                    "(max 200). Optional `filedFrom` / `filedTo` (inclusive dates) filter on when it was " +
                    "filed. Requires `attendance.read`.")
    @GetMapping("/filed-by-me")
    @RequirePermission("attendance.read")
    public com.forward.hrms.commons.web.page.PageResponse<
            com.forward.hrms.attendance.dto.response.ApprovalsInbox.LeaveRow> filedByMe(
            @org.springframework.web.bind.annotation.RequestParam(required = false)
            com.forward.hrms.attendance.enums.LeaveRequestStatus status,
            @org.springframework.web.bind.annotation.RequestParam(required = false) java.time.LocalDate filedFrom,
            @org.springframework.web.bind.annotation.RequestParam(required = false) java.time.LocalDate filedTo,
            @io.swagger.v3.oas.annotations.Parameter(hidden = true)
            org.springframework.data.domain.Pageable pageable) {
        return leaves.filedOnBehalf(status, filedFrom, filedTo,
                com.forward.hrms.commons.web.page.PageableDefaults.apply(pageable, "id"));
    }

    @Operation(summary = "Pending approvals — caller's stage-aware inbox",
            description = "Paginated. Date filter is overlap-style. Default sort newest-first. " +
                    "`q` matches the request id (exact, when numeric), or a case-insensitive substring " +
                    "of the request code, employee code, or employee name. `leaveTypeId` is exact. " +
                    "Inbox-derived; max page size 200. Requires `attendance.read`.")
    @GetMapping("/pending-approvals")
    @RequirePermission("attendance.read")
    public com.forward.hrms.commons.web.page.PageResponse<LeaveRequestDto> pending(
            @RequestParam(required = false) java.time.LocalDate from,
            @RequestParam(required = false) java.time.LocalDate to,
            @io.swagger.v3.oas.annotations.Parameter(description = "Request id, request code, employee code, or employee name.")
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long leaveTypeId,
            @io.swagger.v3.oas.annotations.Parameter(hidden = true)
            org.springframework.data.domain.Pageable pageable) {
        return leaves.pendingApprovals(from, to, q, leaveTypeId, pageable);
    }

    @Operation(summary = "Get a leave request",
            description = "Requires `attendance.read`.")
    @GetMapping("/{id}")
    @RequirePermission("attendance.read")
    public LeaveRequestDto get(@PathVariable long id) {
        return leaves.get(id);
    }

    @Operation(summary = "Per-day breakdown",
            description = "Requires `attendance.read`.")
    @GetMapping("/{id}/days")
    @RequirePermission("attendance.read")
    public List<LeaveDayDto> days(@PathVariable long id) {
        return leaves.listDays(id);
    }

    @Operation(summary = "Approve at the caller's current stage",
            description = "Requires `attendance.write`.")
    @PostMapping("/{id}/approve")
    @RequirePermission("attendance.write")
    public LeaveRequestDto approve(@PathVariable long id, @RequestBody(required = false) ApproveRequest body) {
        return leaves.approve(id, body);
    }

    @Operation(summary = "Reject at the caller's current stage",
            description = "Requires `attendance.write`.")
    @PostMapping("/{id}/reject")
    @RequirePermission("attendance.write")
    public LeaveRequestDto reject(@PathVariable long id, @Valid @RequestBody RejectRequest body) {
        return leaves.reject(id, body);
    }

    @Operation(summary = "Per-day decide (MANAGER stage)",
            description = "MANAGER decides each day APPROVE or REJECT. Once all days are decided, " +
                    "the MANAGER stage closes — APPROVED (advances to HR for ratification) if any " +
                    "day was approved, REJECTED (skips later stages) if every day was rejected. " +
                    "HR cannot redo per-day decisions. Requires `attendance.read`.")
    @ApiResponse(responseCode = "409", description = "STAGE_PER_DAY_NOT_ALLOWED — caller's stage isn't MANAGER.")
    @PostMapping("/{id}/days/decide")
    @RequirePermission("attendance.read")
    public LeaveRequestDto decideDays(@PathVariable long id, @Valid @RequestBody PerDayDecideRequest body) {
        return leaves.decidePerDay(id, body);
    }

    @Operation(summary = "Get employee annual leave balance for calculating annual leave",
            description = "Lists the annual leave balance for the cycle applicable to the calculation month."
    )
    @GetMapping("/employeeId/{employeeId}/annual-leave/{leaveTypeId}/calculation-month/{month}/balance")
    public AnnualLeaveCalcBalanceResponseDto getEmployeeAnnualLeaveBalance(@PathVariable long employeeId, @PathVariable long leaveTypeId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate month) {
        return balances.getEmployeeAnnualLeaveBalance(employeeId, leaveTypeId, month);
    }

    @Operation(summary = "Create leave balance with carry-over from unpaid annual leave",
            description = "Creates a leave balance entry for the next joining anniversary cycle with unpaid leave days carried over. " +
                    "This is called when HR marks an annual leave transaction as unpaid. The carry-over days are stored in the 'carried' field."
    )
    @ApiResponse(responseCode = "201", description = "Created.")
    @ApiResponse(responseCode = "404", description = "LEAVE_TYPE_NOT_FOUND.")
    @ApiResponse(responseCode = "409", description = "LEAVE_BALANCE_ALREADY_EXISTS or LEAVE_TYPE_INACTIVE.")
    @PostMapping("/annual-leave/unpaid-carry")
    @RequirePermission("payroll.admin")
    @ResponseStatus(HttpStatus.CREATED)
    public AnnualLeaveUnpaidCarryResponse createAnnualLeaveUnpaidCarry(
            @Valid @RequestBody AnnualLeaveUnpaidCarryRequest request) {
        var balance = balances.createAnnualLeaveUnpaidCarry(
                request.annualLeaveTransactionId(),
                request.employeeId(),
                request.leaveTypeId(),
                request.carryDays(),
                request.joiningDate()
        );
        return new AnnualLeaveUnpaidCarryResponse(
                balance.getId(),
                balance.getEmployeeId(),
                balance.getCycleStart(),
                balance.getCycleEnd(),
                balance.getCarried().toPlainString()
        );
    }

    @Operation(summary = "Remove leave balance created from unpaid annual leave",
            description = "Deletes leave balance entries that were created from an annual leave unpaid transaction. " +
                    "This is called when HR toggles an unpaid annual leave transaction back to paid."
    )
    @ApiResponse(responseCode = "204", description = "Deleted.")
    @DeleteMapping("/annual-leave/unpaid-carry/{annualLeaveTransactionId}")
    @RequirePermission("payroll.admin")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeAnnualLeaveUnpaidCarry(@PathVariable long annualLeaveTransactionId) {
        balances.removeAnnualLeaveUnpaidCarry(annualLeaveTransactionId);
    }
}
