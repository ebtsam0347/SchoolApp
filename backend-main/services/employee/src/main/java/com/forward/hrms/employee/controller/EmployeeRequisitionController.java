package com.forward.hrms.employee.controller;

import com.forward.hrms.commons.web.page.PageResponse;
import com.forward.hrms.commons.web.page.PageableDefaults;
import com.forward.hrms.commons.web.security.RequirePermission;
import com.forward.hrms.employee.dto.request.AssignRequisitionDetailEmployeeRequest;
import com.forward.hrms.employee.dto.request.EmployeeRequisitionRequestNewHiring;
import com.forward.hrms.employee.dto.request.EmployeeRequisitionRequestReplacement;
import com.forward.hrms.employee.dto.request.LeanRemoveRequisitionDetailRequest;
import com.forward.hrms.employee.dto.request.RejectRequisitionRequest;
import com.forward.hrms.employee.dto.request.UpdateRequisitionRequest;
import com.forward.hrms.employee.dto.response.RequisitionDetailDto;
import com.forward.hrms.employee.dto.response.RequisitionDto;
import com.forward.hrms.employee.enums.RequisitionStatus;
import com.forward.hrms.employee.enums.RequisitionType;
import com.forward.hrms.employee.service.EmployeeRequisitionDetailService;
import com.forward.hrms.employee.service.EmployeeRequisitionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import java.util.List;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Employee Requisitions",
        description = "Manage requests for new hires and replacements. " +
                "Flow: DRAFT → PENDING_APPROVAL → APPROVED → FULFILLED. NEW_HIRE and REPLACEMENT run " +
                "different approval chains (Manufacturing Excellence → HR → D-2 → Director for " +
                "NEW_HIRE; Manufacturing Excellence → HR → D-2 for REPLACEMENT). ")
@RestController
@RequiredArgsConstructor
@RequestMapping("/requisitions")
public class EmployeeRequisitionController {

    private final EmployeeRequisitionService employeeRequisitionService;
    private final EmployeeRequisitionDetailService employeeRequisitionDetailService;

    @Operation(summary = "Create a requisition for New Hiring (starts in DRAFT)",
            description = "Requires ``")
    @ApiResponse(responseCode = "201", description = "Requisition created.")
    @PostMapping("/new-hiring")
    @ResponseStatus(HttpStatus.CREATED)
    public RequisitionDto createNewHiring(@Valid @RequestBody EmployeeRequisitionRequestNewHiring req) {
        return employeeRequisitionService.createNewHiring(req);
    }

    @Operation(summary = "Create a requisition for Replacement (starts in DRAFT)",
            description = "Requires ``. For REPLACEMENT type, `replacingEmployeeId` is mandatory.")
    @ApiResponse(responseCode = "201", description = "Requisition created.")
    @PostMapping("/replacement")
    @ResponseStatus(HttpStatus.CREATED)
    public RequisitionDto createReplacement(@Valid @RequestBody EmployeeRequisitionRequestReplacement req) {
        return employeeRequisitionService.createReplacement(req);
    }

    @Operation(summary = "Update a DRAFT requisition",
            description = "Requires ``. Only null fields are skipped.")
    @ApiResponse(responseCode = "404", description = "Requisition not found.")
    @ApiResponse(responseCode = "409", description = "Requisition is not in DRAFT status.")
    @PatchMapping("/{id}")
    public RequisitionDto update(@PathVariable long id,
                                 @Valid @RequestBody UpdateRequisitionRequest req) {
        return employeeRequisitionService.update(id, req);
    }

    @Operation(summary = "Approve a pending requisition (PENDING_APPROVAL → APPROVED)",
            description = "Requires ``; the caller must additionally be the assignee/pool " +
                    "member for the current stage (department head, HR, or the target grade) — enforced " +
                    "by the approval chain, not this permission. Approving stage 1 closes the lean review " +
                    "and settles `total_positions_lean` as the count of detail rows with " +
                    "`is_lean_removed = false` — so an untouched NEW_HIRE keeps its full HOD total and a " +
                    "REPLACEMENT always keeps every row. Stages 2 and 3 do not change the counts.")
    @ApiResponse(responseCode = "403", description = "Caller cannot act on the current stage.")
    @ApiResponse(responseCode = "409", description = "Requisition is not in PENDING_APPROVAL status.")
    @PostMapping("/{id}/approve")
    public RequisitionDto approve(@PathVariable long id) {
        return employeeRequisitionService.approve(id);
    }

    @Operation(summary = "Reject a pending requisition (PENDING_APPROVAL → REJECTED)",
            description = "Requires ``; the caller must additionally be the assignee/pool " +
                    "member for the current stage — enforced by the approval chain, not this permission.")
    @ApiResponse(responseCode = "403", description = "Caller cannot act on the current stage.")
    @ApiResponse(responseCode = "409", description = "Requisition is not in PENDING_APPROVAL status.")
    @PostMapping("/{id}/reject")
    public RequisitionDto reject(@PathVariable long id,
                                 @Valid @RequestBody RejectRequisitionRequest req) {
        return employeeRequisitionService.reject(id, req);
    }

    @Operation(summary = "Cancel a requisition (DRAFT or PENDING_APPROVAL → CANCELLED)",
            description = "Requires ``. The requester can cancel their own.")
    @ApiResponse(responseCode = "403", description = "Caller is not the requester and lacks employee.admin.")
    @ApiResponse(responseCode = "409", description = "Requisition is not cancellable in its current status.")
    @PostMapping("/{id}/cancel")
    public RequisitionDto cancel(@PathVariable long id) {
        return employeeRequisitionService.cancel(id);
    }

    @Operation(summary = "Mark an approved requisition as fulfilled (APPROVED → FULFILLED)",
            description = "Requires ``. Call this when a hire has been made against the requisition.")
    @ApiResponse(responseCode = "409", description = "Requisition is not in APPROVED status.")
    @PostMapping("/{id}/fulfill")
    public RequisitionDto fulfill(@PathVariable long id) {
        return employeeRequisitionService.fulfill(id);
    }

    @Operation(summary = "Pending approvals — caller's stage-aware inbox",
            description = "Paginated. Merges the caller's inbox across both the NEW_HIRE and REPLACEMENT " +
                    "chains. Date filter is overlap-style (windows on createdAt). Default sort " +
                    "newest-first. Inbox-derived; max page size 200. `q` matches the request id exactly; " +
                    "`type` narrows to one chain; `departmentId` is exact. Requires `` — matches " +
                    "the gate on `/approve` and `/reject` above; only rows the caller can actually act on " +
                    "are returned.")
    @GetMapping("/pending-approvals")
    public PageResponse<RequisitionDto> pendingApprovals(
            @RequestParam(required = false) java.time.LocalDate from,
            @RequestParam(required = false) java.time.LocalDate to,
            @Parameter(description = "Exact request id.")
            @RequestParam(required = false) String q,
            @Parameter(description = "Exact requisition type.")
            @RequestParam(required = false) RequisitionType type,
            @Parameter(description = "Exact department id.")
            @RequestParam(required = false) Long departmentId,
            @Parameter(hidden = true) Pageable pageable) {
        return employeeRequisitionService.pendingApprovals(from, to, q, type, departmentId, pageable);
    }

    @Operation(summary = "Get a single requisition", description = "Requires ``.")
    @ApiResponse(responseCode = "404", description = "Requisition not found.")
    @GetMapping("/{id}")
    public RequisitionDto get(@PathVariable long id) {
        return employeeRequisitionService.get(id);
    }

    @Operation(summary = "Get the requested positions of a requisition",
            description = "Returns the `employee_requisition_detail` rows of the requisition, oldest first. " +
                    "Each row carries its resolved section / designation / grade labels, and — for REPLACEMENT — " +
                    "the code and name of the employee being replaced. Omit `leanRemoved` for every row.")
    @ApiResponse(responseCode = "404", description = "Requisition not found.")
    @GetMapping("/{id}/details")
    public List<RequisitionDetailDto> details(
            @PathVariable long id,
            @Parameter(description = "Omit for all rows; `false` for positions still standing, "
                    + "`true` for the ones removed during lean review.")
            @RequestParam(required = false) Boolean leanRemoved) {
        return employeeRequisitionDetailService.listByRequisitionId(id, leanRemoved);
    }

    @Operation(summary = "Remove a requested position during Manufacturing Excellence review (NEW_HIRE only)",
            description = "Flags the detail row `is_lean_removed = true` with the supplied remark and the " +
                    "acting user, then re-derives `total_positions_lean` on the head as the count of rows " +
                    "still standing. Legal only while the requisition is PENDING_APPROVAL and stage 1 " +
                    "(Manufacturing Excellence Review) is the pending stage, and only for the reviewer who " +
                    "can act on that stage. REPLACEMENT requisitions are refused — a replacement is always " +
                    "required. The chain is not advanced: call `/approve` when done trimming, which settles " +
                    "`total_positions_lean` and moves the request to stage 2.")
    @ApiResponse(responseCode = "403", description = "Caller cannot act on the Manufacturing Excellence stage.")
    @ApiResponse(responseCode = "404", description = "Requisition or detail row not found.")
    @ApiResponse(responseCode = "409",
            description = "Requisition is REPLACEMENT, is not PENDING_APPROVAL, is past stage 1, " +
                    "or the row is already removed.")
    @PostMapping("/{id}/details/{detailId}/lean-remove")
    public RequisitionDto leanRemoveDetail(@PathVariable long id,
                                           @PathVariable long detailId,
                                           @Valid @RequestBody LeanRemoveRequisitionDetailRequest req) {
        return employeeRequisitionDetailService.leanRemove(id, detailId, req);
    }

    @Operation(summary = "Record who was hired against an approved position",
            description = "Sets `new_employee_code` and/or `temporary_employee_reference` on one detail row. " +
                    "Requires `employee.admin`. Legal only once the requisition is APPROVED and every " +
                    "approval stage is decided — no pending stage left. A null field is left unchanged, so " +
                    "the temporary reference can be captured first and the real employee code written over " +
                    "it later. Rows removed during lean review cannot be filled.")
    @ApiResponse(responseCode = "403", description = "Caller lacks employee.admin.")
    @ApiResponse(responseCode = "404", description = "Requisition or detail row not found.")
    @ApiResponse(responseCode = "409",
            description = "Requisition is not APPROVED, still has an undecided stage, or the row was lean-removed.")
    @ApiResponse(responseCode = "422", description = "Neither field was supplied.")
    @RequirePermission("employee.admin")
    @PatchMapping("/{id}/details/{detailId}")
    public RequisitionDetailDto assignDetailEmployee(@PathVariable long id,
                                                     @PathVariable long detailId,
                                                     @Valid @RequestBody AssignRequisitionDetailEmployeeRequest req) {
        return employeeRequisitionDetailService.assignEmployeeReference(id, detailId, req);
    }

    @Operation(summary = "List requisitions (paginated)",
            description = "All filters are optional and combinable. Requires ``.")
    @GetMapping
    public PageResponse<RequisitionDto> list(
            @Parameter(description = "Filter by status.") @RequestParam(required = false) RequisitionStatus status,
            @Parameter(description = "Filter by type.") @RequestParam(required = false) RequisitionType type,
            @Parameter(description = "Filter by department.") @RequestParam(required = false) Long departmentId,
            @Parameter(hidden = true) Pageable pageable) {
        return employeeRequisitionService.list(status, type, departmentId,
                PageableDefaults.apply(pageable, "createdAt"));
    }

    @Operation(summary = "List requisitions created by the caller or within the caller's department",
            description = "All filters are optional. Requires authentication.")
    public PageResponse<RequisitionDto> getRequisitionListSelfOrWithInDepartment(
            @Parameter(description = "Filter by status.") @RequestParam(required = false) RequisitionStatus status,
            @Parameter(description = "Filter by type.") @RequestParam(required = false) RequisitionType type,
            @Parameter(hidden = true) Pageable pageable) {
        return employeeRequisitionService.requisitionListSelfOrWithInDepartment(status, type,
                PageableDefaults.apply(pageable, "createdAt"));
    }

    @Operation(summary = "List all requisitions for HR ends.",
            description = "All filter are optional. Requires `employee.admin`")
    @GetMapping("/hr-list")
    @RequirePermission("employee.admin")
    public PageResponse<RequisitionDto> getRequisitionHrList(
            @Parameter(description = "Filter by status.") @RequestParam(required = false) RequisitionStatus status,
            @Parameter(description = "Filter by type.") @RequestParam(required = false) RequisitionType type,
            @Parameter(description = "Filter by department.") @RequestParam(required = false) Long departmentId,
            @Parameter(description = "Filter by id.") @RequestParam(required = false) Long id,
            @Parameter(description = "Filter by Job description") @RequestParam(required = false)  String jobDescription,
            @Parameter(hidden = true) Pageable pageable) {
        return employeeRequisitionService.requisitionListHr(status, type, departmentId, id, jobDescription,
                PageableDefaults.apply(pageable, "createdAt"));
    }
}
