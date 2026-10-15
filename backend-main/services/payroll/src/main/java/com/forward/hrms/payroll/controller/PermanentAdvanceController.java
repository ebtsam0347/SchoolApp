package com.forward.hrms.payroll.controller;

import com.forward.hrms.commons.web.page.PageResponse;
import com.forward.hrms.commons.web.page.PageableDefaults;
import com.forward.hrms.commons.web.security.RequirePermission;
import com.forward.hrms.payroll.dto.request.AdjustPermanentAdvanceRequest;
import com.forward.hrms.payroll.dto.request.AdvanceDecisionRequest;
import com.forward.hrms.payroll.dto.request.AdvanceRejectRequest;
import com.forward.hrms.payroll.dto.request.CreatePermanentAdvanceRequest;
import com.forward.hrms.payroll.dto.request.RecordPermanentAdvanceDeductionRequest;
import com.forward.hrms.payroll.dto.response.PermanentAdvanceDeductionDto;
import com.forward.hrms.payroll.dto.response.PermanentAdvanceDto;
import com.forward.hrms.payroll.dto.response.PermanentAdvanceInboxRow;
import com.forward.hrms.payroll.enums.AdvanceStatus;
import com.forward.hrms.payroll.service.PermanentAdvanceService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Permanent advances",
        description = "Long-term salary advances. Three-stage approval (Manager → HR → Accounts) → " +
                "APPROVED → monthly deductions from the payroll run.Requires `payroll.read` / `payroll.write` / " +
                "`payroll.advance.hr` / `payroll.advance.accounts`.")
@RestController
@RequestMapping("/permanent-advances")
@RequiredArgsConstructor
public class PermanentAdvanceController {

    private final PermanentAdvanceService advances;

    @Operation(summary = "List permanent advances",
            description = "Default sort by id desc, page size 20 (max 200). Requires `payroll.read`.")
    @GetMapping
    @RequirePermission("payroll.read")
    public PageResponse<PermanentAdvanceDto> list(
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) AdvanceStatus status,
            @Parameter(hidden = true) Pageable pageable) {
        return advances.search(employeeId, status, PageableDefaults.apply(pageable, "id"));
    }

    @Operation(summary = "HR-approved receiving list — awaiting Accounts approval (advance-slip list)",
            description = "Advances HR has approved that Accounts has not yet approved (status HR_APPROVED). " +
                    "HR uses this list to print the advance slip. Optional `employeeId` narrows it to one " +
                    "employee. Default sort by id desc, page size 20 (max 200). Requires `payroll.advance.hr`.")
    @GetMapping("/hr-approved-receiving-list")
    @RequirePermission("payroll.advance.hr")
    public PageResponse<PermanentAdvanceDto> hrApprovedReceivingList(
            @RequestParam(required = false) Long employeeId,
            @Parameter(hidden = true) Pageable pageable) {
        return advances.hrApprovedReceivingList(employeeId, PageableDefaults.apply(pageable, "id"));
    }

    @Operation(summary = "Get one", description = "Requires `payroll.read`.")
    @GetMapping("/{id}")
    @RequirePermission("payroll.read")
    public PermanentAdvanceDto get(@PathVariable long id) {
        return advances.get(id);
    }

    @Operation(summary = "My department's advances",
            description = "Paginated list of every advance covering someone in the caller's own " +
                    "department, whoever filed it, with approval `steps` / `currentStage`. " +
                    "Optional `status`, `filedFrom` / `filedTo` (inclusive dates, on when it " +
                    "was filed) and `employeeId` (a colleague; outside the department -> empty). " +
                    "Default sort is `id desc`. Requires `team.request.generation.longTermAdvance`.")
    @GetMapping("/department")
    @RequirePermission("team.request.generation.longTermAdvance")
    public PageResponse<PermanentAdvanceDto> department(
            @RequestParam(required = false) AdvanceStatus status,
            @RequestParam(required = false) java.time.LocalDate filedFrom,
            @RequestParam(required = false) java.time.LocalDate filedTo,
            @RequestParam(required = false) Long employeeId,
            @Parameter(hidden = true) Pageable pageable) {
        return advances.forDepartment(status, filedFrom, filedTo, employeeId, PageableDefaults.apply(pageable, "id"));
    }

    @Operation(summary = "Advances I filed for others",
            description = "Paginated list of permanent advances the caller filed on someone else's " +
                    "behalf (e.g. a team-request generator for a colleague). The caller's own " +
                    "self-applications are excluded. Each row carries its approval `steps` / " +
                    "`currentStage`. Scoped to the caller, so no permission beyond authentication. " +
                    "Optional `filedFrom` / `filedTo` (inclusive dates) filter on when it was filed. " +
                    "Default sort is `id desc`.")
    @GetMapping("/filed-by-me")
    public PageResponse<PermanentAdvanceDto> filedByMe(
            @RequestParam(required = false) AdvanceStatus status,
            @RequestParam(required = false) java.time.LocalDate filedFrom,
            @RequestParam(required = false) java.time.LocalDate filedTo,
            @Parameter(hidden = true) Pageable pageable) {
        return advances.filedOnBehalf(status, filedFrom, filedTo, PageableDefaults.apply(pageable, "id"));
    }

    @Operation(summary = "Pending approvals — caller's stage-aware inbox",
            description = "Merges all three review stages into one list: PENDING rows where the caller is " +
                    "the resolved manager (not permission-gated — enforced by the approval chain), " +
                    "MANAGER_APPROVED rows if the caller holds `payroll.advance.hr`, and HR_APPROVED rows " +
                    "if they hold `payroll.advance.accounts`. Each row's `approvalStage` says which stage " +
                    "it's at. `q` matches the advance id (exact, when numeric) or a case-insensitive " +
                    "substring of the request code, employee code, or employee name. Paginated, " +
                    "newest-first, max page size 200.")
    @GetMapping("/pending-approvals")
    public PageResponse<PermanentAdvanceInboxRow> pendingApprovals(
            @Parameter(description = "Advance id, request code, employee code, or employee name.")
            @RequestParam(required = false) String q,
            @Parameter(hidden = true) Pageable pageable) {
        return advances.pendingApprovals(q, pageable);
    }

    @Operation(summary = "Self-serve: list permanent advances for an employee",
            description = "Self-serve when the path id matches the caller's employee id (resolved " +
                    "via employee-service /me). HR / payroll-officer callers with `payroll.read` " +
                    "may read any employee. Default sort is `id desc`.")
    @GetMapping("/employees/{id}")
    public PageResponse<PermanentAdvanceDto> forEmployee(
            @PathVariable("id") long employeeId,
            @RequestParam(required = false) AdvanceStatus status,
            @Parameter(hidden = true) Pageable pageable) {
        return advances.listForEmployee(employeeId, status, PageableDefaults.apply(pageable, "id"));
    }

    @Operation(summary = "Create — opens in PENDING",
            description = "Self-apply when the request's `employeeId` matches the caller's own " +
                    "employee id (resolved via employee-service /me) — goes to the caller's manager, " +
                    "even for a MANAGER. A MANAGER may file for their direct reports or department; " +
                    "its Manager stage is auto-approved, so it goes straight to HR. A " +
                    "TEAM_REQUEST_GENERATION caller may file for their department; the Manager stage " +
                    "goes to the caller's own manager. One advance per employee per calendar month " +
                    "(rejected / cancelled ones don't count).")
    @ApiResponse(responseCode = "201", description = "Created.")
    @ApiResponse(responseCode = "403", description = "Caller may not file for this employee.")
    @ApiResponse(responseCode = "409", description = "Employee already has an advance filed this month.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PermanentAdvanceDto create(@Valid @RequestBody CreatePermanentAdvanceRequest req) {
        return advances.create(req);
    }

    @Operation(summary = "Manager-approve — PENDING → MANAGER_APPROVED",
            description = "Caller must be the requester's resolved manager (enforced by the approval " +
                    "chain, not a permission — a manager holds no `payroll.*` permission).")
    @PostMapping("/{id}/manager-approve")
    public PermanentAdvanceDto managerApprove(@PathVariable long id,
                                              @RequestBody(required = false) AdvanceDecisionRequest body) {
        return advances.managerApprove(id, body);
    }

    @Operation(summary = "HR adjust before approval — revise amount / monthly deduction",
            description = "Lets HR revise the principal `amount` and `monthlyDeduction` of an advance that is " +
                    "waiting on HR (status MANAGER_APPROVED), before HR approves it. Once HR has approved, the " +
                    "figures are locked. Status and approval chain are unchanged — HR still calls " +
                    "`hr-approve` afterwards. Every change is audited (before / after / note). " +
                    "Requires `payroll.advance.hr`.")
    @ApiResponse(responseCode = "409", description = "Advance is not waiting on HR, or monthly deduction exceeds amount.")
    @PostMapping("/{id}/hr-adjust-before-approval")
    @RequirePermission("payroll.advance.hr")
    public PermanentAdvanceDto hrAdjustBeforeApproval(@PathVariable long id,
                                                      @Valid @RequestBody AdjustPermanentAdvanceRequest body) {
        return advances.hrAdjustBeforeApproval(id, body);
    }

    @Operation(summary = "HR-approve — MANAGER_APPROVED → HR_APPROVED", description = "Requires `payroll.advance.hr`.")
    @PostMapping("/{id}/hr-approve")
    @RequirePermission("payroll.advance.hr")
    public PermanentAdvanceDto hrApprove(@PathVariable long id,
                                         @RequestBody(required = false) AdvanceDecisionRequest body) {
        return advances.hrApprove(id, body);
    }

    @Operation(summary = "Accounts-approve — HR_APPROVED → APPROVED", description = "Requires `payroll.advance.accounts`.")
    @PostMapping("/{id}/accounts-approve")
    @RequirePermission("payroll.advance.accounts")
    public PermanentAdvanceDto accountsApprove(@PathVariable long id,
                                               @RequestBody(required = false) AdvanceDecisionRequest body) {
        return advances.accountsApprove(id, body);
    }

    @Operation(summary = "Reject — PENDING / MANAGER_APPROVED / HR_APPROVED → REJECTED",
            description = "The manager, HR, or Accounts reviewer can reject at their own pending stage " +
                    "— enforced by the approval chain, not a permission (a manager holds none of the " +
                    "`payroll.advance.*` ones).")
    @PostMapping("/{id}/reject")
    public PermanentAdvanceDto reject(@PathVariable long id, @Valid @RequestBody AdvanceRejectRequest body) {
        return advances.reject(id, body);
    }

    @Operation(summary = "Cancel — before final approval only", description = "Requires `payroll.write`.")
    @PostMapping("/{id}/cancel")
    @RequirePermission("payroll.write")
    public PermanentAdvanceDto cancel(@PathVariable long id) {
        return advances.cancel(id);
    }

    @Operation(summary = "List deductions for an advance", description = "Requires `payroll.read`.")
    @GetMapping("/{id}/deductions")
    @RequirePermission("payroll.read")
    public List<PermanentAdvanceDeductionDto> listDeductions(@PathVariable long id) {
        return advances.listDeductions(id);
    }

    @Operation(summary = "Record an out-of-band deduction",
            description = "Manual deduction, always recorded as `deducted_amount`. Capped at the employee's " +
                    "remaining balance (all APPROVED advances minus all deductions). Requires `payroll.advance.accounts`.")
    @PostMapping("/{id}/deductions")
    @RequirePermission("payroll.advance.accounts")
    @ResponseStatus(HttpStatus.CREATED)
    public PermanentAdvanceDeductionDto recordDeduction(
            @PathVariable long id, @Valid @RequestBody RecordPermanentAdvanceDeductionRequest req) {
        return advances.recordDeduction(id, req);
    }

    @Operation(summary = "Delete a manual deduction entered by mistake",
            description = "Only `MANUAL` deductions. Salary, bonus, annual-leave and settlement deductions are " +
                    "removed by undoing their source (payroll run reset, settlement reject/cancel). " +
                    "Requires `payroll.advance.accounts`.")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @ApiResponse(responseCode = "404", description = "No such deduction on this advance")
    @ApiResponse(responseCode = "409", description = "Not a MANUAL deduction")
    @DeleteMapping("/{id}/deductions/{deductionId}")
    @RequirePermission("payroll.advance.accounts")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDeduction(@PathVariable long id, @PathVariable long deductionId) {
        advances.deleteDeduction(id, deductionId);
    }
}
