package com.forward.hrms.payroll.controller;

import com.forward.hrms.commons.web.page.PageResponse;
import com.forward.hrms.commons.web.page.PageableDefaults;
import com.forward.hrms.commons.web.security.RequirePermission;
import com.forward.hrms.payroll.dto.request.AdvanceDecisionRequest;
import com.forward.hrms.payroll.dto.request.AdvanceRejectRequest;
import com.forward.hrms.payroll.dto.request.CreateShortTermAdvanceBatchRequest;
import com.forward.hrms.payroll.dto.response.ShortTermAdvanceBatchDto;
import com.forward.hrms.payroll.dto.response.ShortTermAdvanceInboxRow;
import com.forward.hrms.payroll.dto.response.ShortTermAdvanceLineDto;
import com.forward.hrms.payroll.enums.AdvanceStatus;
import com.forward.hrms.payroll.service.ShortTermAdvanceService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Short-term advances",
        description = "Bulk advance request — N employees, single target month deduction. " +
                "Manager → HR → Accounts approval → APPROVED. Requires `payroll.read` / `payroll.write` / " +
                "`payroll.advance.hr` / `payroll.advance.accounts`.")
@RestController
@RequestMapping("/short-term-advance-batches")
@RequiredArgsConstructor
public class ShortTermAdvanceController {

    private final ShortTermAdvanceService service;

    @Operation(summary = "List batches",
            description = "Default sort by id desc. Requires `payroll.read`.")
    @GetMapping
    public PageResponse<ShortTermAdvanceBatchDto> list(
            @RequestParam(required = false) AdvanceStatus status,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Long departmentId,
            @Parameter(hidden = true) Pageable pageable) {
        return service.search(status, year, month, departmentId,
                PageableDefaults.apply(pageable, "id"));
    }

    @Operation(summary = "Get one (with lines)", description = "Requires `payroll.read`.")
    @GetMapping("/{id}")
    public ShortTermAdvanceBatchDto get(@PathVariable long id) {
        return service.get(id);
    }

    @Operation(summary = "My department's short-term advance batches",
            description = "Paginated list of every short-term advance batche covering someone in the caller's own " +
                    "department, whoever filed it, with approval `steps` / `currentStage`. " +
                    "Optional `status`, `filedFrom` / `filedTo` (inclusive dates, on when it " +
                    "was filed) and `employeeId` (a colleague; outside the department -> empty). " +
                    "Default sort is `id desc`. Requires `team.request.generation.shortTermAdvance`.")
    @GetMapping("/department")
    @RequirePermission("team.request.generation.shortTermAdvance")
    public PageResponse<ShortTermAdvanceBatchDto> department(
            @RequestParam(required = false) AdvanceStatus status,
            @RequestParam(required = false) java.time.LocalDate filedFrom,
            @RequestParam(required = false) java.time.LocalDate filedTo,
            @RequestParam(required = false) Long employeeId,
            @Parameter(hidden = true) Pageable pageable) {
        return service.forDepartment(status, filedFrom, filedTo, employeeId, PageableDefaults.apply(pageable, "id"));
    }

    @Operation(summary = "Batches I raised",
            description = "Paginated list of short-term advance batches the caller created, with " +
                    "lines, approval `steps` and `currentStage`. Scoped to the caller, so no " +
                    "permission beyond authentication. Optional `filedFrom` / `filedTo` (inclusive " +
                    "dates) filter on when it was filed. Default sort is `id desc`.")
    @GetMapping("/filed-by-me")
    public PageResponse<ShortTermAdvanceBatchDto> filedByMe(
            @RequestParam(required = false) AdvanceStatus status,
            @RequestParam(required = false) java.time.LocalDate filedFrom,
            @RequestParam(required = false) java.time.LocalDate filedTo,
            @Parameter(hidden = true) Pageable pageable) {
        return service.filedByMe(status, filedFrom, filedTo, PageableDefaults.apply(pageable, "id"));
    }

    @Operation(summary = "Pending approvals — caller's stage-aware inbox",
            description = "Merges all three review stages into one list: PENDING batches where the caller " +
                    "is the resolved manager (not permission-gated — enforced by the approval chain), " +
                    "MANAGER_APPROVED batches if the caller holds `payroll.advance.hr`, and HR_APPROVED " +
                    "batches if they hold `payroll.advance.accounts`. Each row's `approvalStage` says which " +
                    "stage it's at. `q` matches the batch id (exact, when numeric) or a case-insensitive " +
                    "substring of the request code. Paginated, newest-first, max page size 200.")
    @GetMapping("/pending-approvals")
    public PageResponse<ShortTermAdvanceInboxRow> pendingApprovals(
            @Parameter(description = "Batch id or request code.")
            @RequestParam(required = false) String q,
            @Parameter(hidden = true) Pageable pageable) {
        return service.pendingApprovals(q, pageable);
    }

    @Operation(summary = "My short-term advances",
            description = "Paginated list of batches carrying a line for the caller. `lines`, " +
                    "`totalAmount` and `totalEmployees` are rescoped to the caller's own line, so " +
                    "batch-mates never show. Optional `status`, `filedFrom` / `filedTo` (inclusive " +
                    "dates, on when it was filed). Self-scoped, so no permission beyond " +
                    "authentication. Default sort `id desc`.")
    @GetMapping("/me")
    public PageResponse<ShortTermAdvanceBatchDto> mine(
            @RequestParam(required = false) AdvanceStatus status,
            @RequestParam(required = false) java.time.LocalDate filedFrom,
            @RequestParam(required = false) java.time.LocalDate filedTo,
            @Parameter(hidden = true) Pageable pageable) {
        return service.mine(status, filedFrom, filedTo, PageableDefaults.apply(pageable, "id"));
    }

    @Operation(summary = "My team's short-term advances",
            description = "Paginated list of batches carrying a line for any of the caller's direct " +
                    "reports, whoever raised them. `lines`, `totalAmount` and `totalEmployees` are " +
                    "rescoped to those reports' lines. Empty for a caller who manages nobody. Same " +
                    "filters as `/me`. Default sort `id desc`.")
    @GetMapping("/team")
    public PageResponse<ShortTermAdvanceBatchDto> team(
            @RequestParam(required = false) AdvanceStatus status,
            @RequestParam(required = false) java.time.LocalDate filedFrom,
            @RequestParam(required = false) java.time.LocalDate filedTo,
            @Parameter(hidden = true) Pageable pageable) {
        return service.team(status, filedFrom, filedTo, PageableDefaults.apply(pageable, "id"));
    }

    @Operation(summary = "List per-employee lines for a batch",
            description = "Per-employee lines of a short-term advance batch, ordered by employee id. Each " +
                    "line carries an `employee` summary (code, name, department, section, designation) " +
                    "resolved from the shared employee cache. Authentication only — no permission check.")
    @ApiResponse(responseCode = "404", description = "No batch with that id.")
    @GetMapping("/{id}/lines")
    public List<ShortTermAdvanceLineDto> listLines(@PathVariable long id) {
        return service.listLines(id);
    }

    @Operation(summary = "Self-serve: list batches for an employee",
            description = "Self-serve when the path employeeId matches the caller's employee id " +
                    "(resolved via employee-service /me) — `lines`, `totalAmount`, and `totalEmployees` " +
                    "are rescoped to just the caller's own line so batch-mates from HR's bulk-apply flow " +
                    "don't leak through. HR / payroll-officer callers with `payroll.read` may read any " +
                    "employeeId and see the full batch. Default sort is `id desc`.")
    @GetMapping("/employees/{employeeId}")
    public PageResponse<ShortTermAdvanceBatchDto> forEmployee(
            @PathVariable long employeeId,
            @RequestParam(required = false) AdvanceStatus status,
            @Parameter(hidden = true) Pageable pageable) {
        return service.listForEmployee(employeeId, status, PageableDefaults.apply(pageable, "id"));
    }

    @Operation(summary = "Create — opens batch + lines in PENDING",
            description = "Self-apply when every line's employeeId matches the caller's own employee id " +
                    "(resolved via employee-service /me) — goes to the caller's manager, even for a " +
                    "MANAGER. A MANAGER may raise a batch for their direct reports or department (not " +
                    "including themselves); its Manager stage is auto-approved (status MANAGER_APPROVED), " +
                    "so it goes straight to HR, then Accounts. A TEAM_REQUEST_GENERATION caller may raise " +
                    "one for employees in their own department; the Manager stage goes to the caller's own " +
                    "manager and is not auto-approved.")
    @ApiResponse(responseCode = "403", description = "Caller may not raise a batch for these employees.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ShortTermAdvanceBatchDto create(@Valid @RequestBody CreateShortTermAdvanceBatchRequest req) {
        return service.create(req);
    }

    @Operation(summary = "Manager-approve — PENDING → MANAGER_APPROVED",
            description = "Caller must be the resolved manager of whoever raised the batch — enforced " +
                    "by the approval chain, not a permission.")
    @PostMapping("/{id}/manager-approve")
    public ShortTermAdvanceBatchDto managerApprove(@PathVariable long id,
                                                   @RequestBody(required = false) AdvanceDecisionRequest body) {
        return service.managerApprove(id, body);
    }

    @Operation(summary = "HR-approve — MANAGER_APPROVED → HR_APPROVED", description = "Requires `payroll.advance.hr`.")
    @PostMapping("/{id}/hr-approve")
    @RequirePermission("payroll.advance.hr")
    public ShortTermAdvanceBatchDto hrApprove(@PathVariable long id,
                                              @RequestBody(required = false) AdvanceDecisionRequest body) {
        return service.hrApprove(id, body);
    }

    @Operation(summary = "Accounts-approve — flips lines to APPROVED",
            description = "Requires `payroll.advance.accounts`.")
    @PostMapping("/{id}/accounts-approve")
    @RequirePermission("payroll.advance.accounts")
    public ShortTermAdvanceBatchDto accountsApprove(@PathVariable long id,
                                                    @RequestBody(required = false) AdvanceDecisionRequest body) {
        return service.accountsApprove(id, body);
    }

    @Operation(summary = "Reject — PENDING / MANAGER_APPROVED / HR_APPROVED → REJECTED",
            description = "The manager, HR, or Accounts reviewer can reject at their own pending stage " +
                    "— enforced by the approval chain, not a permission.")
    @PostMapping("/{id}/reject")
    public ShortTermAdvanceBatchDto reject(@PathVariable long id, @Valid @RequestBody AdvanceRejectRequest body) {
        return service.reject(id, body);
    }

    @Operation(summary = "Cancel — pre-deduction only", description = "Requires `payroll.write`.")
    @PostMapping("/{id}/cancel")
    public ShortTermAdvanceBatchDto cancel(@PathVariable long id) {
        return service.cancel(id);
    }
}
