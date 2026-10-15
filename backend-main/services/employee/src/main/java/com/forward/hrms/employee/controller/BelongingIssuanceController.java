package com.forward.hrms.employee.controller;

import com.forward.hrms.commons.web.security.RequirePermission;
import com.forward.hrms.employee.dto.request.BelongingClearanceRequest;
import com.forward.hrms.employee.dto.request.BelongingIssuanceRequest;
import com.forward.hrms.employee.dto.response.BelongingClearanceCheckDto;
import com.forward.hrms.employee.dto.response.BelongingIssuanceResponse;
import com.forward.hrms.employee.enums.belonging.ActivationStatus;
import com.forward.hrms.employee.service.BelongingIssuanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Belonging Issuance", description = "Manage the employee belonging issuance.")
@RestController
@RequestMapping("/belonging-issuance")
@RequiredArgsConstructor
public class BelongingIssuanceController {

    private final BelongingIssuanceService service;

    @Operation(summary = "Issue a belonging",
            description = "Issues a belonging to an active employee."
    )
    @PostMapping
    public BelongingIssuanceResponse issue(
            @Valid @RequestBody BelongingIssuanceRequest request) {
        return service.issue(request);
    }

    @Operation(summary = "Get belonging issuance by ID",
            description = "Retrieves a belonging issuance record using its unique ID."
    )
    @GetMapping("/{id}")
    public BelongingIssuanceResponse getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @Operation(summary = "Get active belongings of an employee",
            description = "Returns all active belongings assigned to the specified employee."
    )
    @GetMapping("/employee/{employeeId}/active")
    public List<BelongingIssuanceResponse> getActiveByEmployee(
            @PathVariable Long employeeId) {
        return service.getActiveBelongings(employeeId);
    }

    @Operation(summary = "Get all active belongings",
            description = "Returns all currently active belonging issuance records."
    )
    @GetMapping("/active")
    public List<BelongingIssuanceResponse> getAllActive() {
        return service.getAllActiveBelongings();
    }

    @Operation(summary = "Get all pending clearance records",
            description = "Returns belonging clearance information for all employees."
    )
    @GetMapping("/clearance")
    public List<BelongingIssuanceResponse> getAllClearance() {
        return service.getClearanceStatusForAllEmployees();
    }

    @Operation(summary = "Get clearance status by employee",
            description = "Returns belonging clearance records for the specified employee."
    )
    @GetMapping("/employee/{employeeId}/clearance")
    public List<BelongingIssuanceResponse> getClearanceByEmployee(
            @PathVariable Long employeeId) {
        return service.getClearanceStatusByEmployee(employeeId);
    }

    @Operation(summary = "Termination clearance check",
            description = """
                    Whether the employee is clear of company belongings, and which \
                    issuance still block them. A belonging blocks while it is `ACTIVE` and its \
                    clearance is not `APPROVED`; `RETURNED` / `LOST` / `DAMAGED` / `INACTIVE` \
                    rows and APPROVED sign-offs (buy-back, write-off) do not. An employee who \
                    was never issued anything comes back `cleared: true` with an empty list.
                    
                    This is the gate payroll-service reads before it lets HR approve a final \
                    settlement — see `POST /settlement/employees/{id}/hr-approval-confirmation`. \
                    Requires `employee.read`."""
    )
    @GetMapping("/employee/{employeeId}/clearance-check")
    @RequirePermission("employee.read")
    public BelongingClearanceCheckDto clearanceCheck(
            @PathVariable Long employeeId) {
        return service.clearanceCheck(employeeId);
    }

    @Operation(summary = "Update belonging clearance",
            description = "Updates the clearance details and status of a belonging issuance."
    )
    @PutMapping("/{id}/clearance")
    public BelongingIssuanceResponse updateClearance(
            @PathVariable Long id,
            @Valid @RequestBody BelongingClearanceRequest request) {
        return service.updateClearance(id, request);
    }

    @Operation(summary = "Update activation status",
            description = "Activates or deactivates a belonging issuance."
    )
    @PatchMapping("/{id}/activation")
    public BelongingIssuanceResponse updateActivation(
            @PathVariable Long id,
            @RequestParam ActivationStatus status) {
        return service.updateActivationStatus(id, status);
    }

    @Operation(summary = "Update belonging issuance",
            description = "Updates all editable details of an existing belonging issuance record."
    )
    @PutMapping("/{id}")
    public BelongingIssuanceResponse updateIssuance(
            @PathVariable Long id,
            @Valid @RequestBody BelongingIssuanceRequest request) {
        return service.updateIssuance(id, request);
    }

}
