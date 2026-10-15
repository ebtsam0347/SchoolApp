package com.forward.hrms.employee.controller;

import com.forward.hrms.commons.web.page.PageResponse;
import com.forward.hrms.commons.web.page.PageableDefaults;
import com.forward.hrms.commons.web.security.RequirePermission;
import com.forward.hrms.employee.dto.request.CreateTourRequest;
import com.forward.hrms.employee.dto.request.RejectTourRequest;
import com.forward.hrms.employee.dto.request.UpdateTourRequest;
import com.forward.hrms.employee.dto.response.FactoryTourDto;
import com.forward.hrms.employee.dto.response.FactoryToursInbox;
import com.forward.hrms.employee.enums.TourStatus;
import com.forward.hrms.employee.enums.TourType;

import com.forward.hrms.employee.service.FactoryTourServiceOld;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

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

@Tag(name = "Factory Tours",
        description = "Manage employee factory-tour travel requests. " +
                "Flow: DRAFT → PENDING_APPROVAL → APPROVED. " +
                "Requires `employee.read` / `employee.tours.write` / `employee.admin`.")
@RestController
@RequiredArgsConstructor
@RequestMapping("/factory-tours")
public class FactoryTourControllerOld {

    private final FactoryTourServiceOld service;

    @Operation(summary = "Create a factory tour travel request (starts in DRAFT)",
            description = "Requester identity (cardNo/name/department/section/designation/grade) is " +
                    "resolved server-side from the caller. Requires `employee.tours.write`.")
    @ApiResponse(responseCode = "201", description = "Tour request created.")
    @PostMapping
    @RequirePermission("employee.tours.write")
    @ResponseStatus(HttpStatus.CREATED)
    public FactoryTourDto create(@Valid @RequestBody CreateTourRequest req) {
        return service.create(req);
    }

    @Operation(summary = "Update a DRAFT tour request",
            description = "Requires `employee.tours.write`.")
    @ApiResponse(responseCode = "409", description = "Tour is not in DRAFT status.")
    @PatchMapping("/{id}")
    @RequirePermission("employee.tours.write")
    public FactoryTourDto update(@PathVariable long id,
                                 @Valid @RequestBody UpdateTourRequest req) {
        return service.update(id, req);
    }

    @Operation(summary = "Submit a DRAFT for approval (DRAFT → PENDING_APPROVAL)",
            description = "Requires `employee.tours.write`.")
    @ApiResponse(responseCode = "409", description = "Tour is not in DRAFT status.")
    @PostMapping("/{id}/submit")
    @RequirePermission("employee.tours.write")
    public FactoryTourDto submit(@PathVariable long id) {
        return service.submit(id);
    }

    @Operation(summary = "Approve the current pending stage",
            description = "Requires `employee.admin`. Status moves to APPROVED only after the final stage.")
    @ApiResponse(responseCode = "409", description = "Tour is not in PENDING_APPROVAL status.")
    @PostMapping("/{id}/approve")
    @RequirePermission("employee.admin")
    public FactoryTourDto approve(@PathVariable long id) {
        return service.approve(id);
    }

    @Operation(summary = "Reject at the current pending stage (→ REJECTED)",
            description = "Requires `employee.admin`.")
    @ApiResponse(responseCode = "409", description = "Tour is not in PENDING_APPROVAL status.")
    @PostMapping("/{id}/reject")
    @RequirePermission("employee.admin")
    public FactoryTourDto reject(@PathVariable long id,
                                 @Valid @RequestBody RejectTourRequest req) {
        return service.reject(id, req);
    }

    @Operation(summary = "Cancel a tour (DRAFT or PENDING_APPROVAL → CANCELLED)",
            description = "Requires `employee.tours.write`. Requester can cancel their own; `employee.admin` can cancel any.")
    @ApiResponse(responseCode = "403", description = "Caller is not the requester and lacks employee.admin.")
    @ApiResponse(responseCode = "409", description = "Tour cannot be cancelled in its current status.")
    @PostMapping("/{id}/cancel")
    @RequirePermission("employee.tours.write")
    public FactoryTourDto cancel(@PathVariable long id) {
        return service.cancel(id);
    }

    @Operation(summary = "Pending approvals inbox for the caller",
            description = "Requires `employee.admin`. Combines LOCAL and INTERNATIONAL tour requests " +
                    "currently awaiting the caller's decision (direct assignment, permission, or delegation).")
    @GetMapping("/pending-approvals")
    @RequirePermission("employee.admin")
    public FactoryToursInbox pendingApprovals() {
        return service.pendingApprovals();
    }

    @Operation(summary = "Get a single tour request", description = "Requires `employee.read`.")
    @ApiResponse(responseCode = "404", description = "Tour not found.")
    @GetMapping("/{id}")
    @RequirePermission("employee.read")
    public FactoryTourDto get(@PathVariable long id) {
        return service.get(id);
    }

    @Operation(summary = "List tour requests (paginated)", description = "Requires `employee.read`.")
    @GetMapping
    @RequirePermission("employee.read")
    public PageResponse<FactoryTourDto> list(
            @Parameter(description = "Filter by status.") @RequestParam(required = false) TourStatus status,
            @Parameter(description = "Filter by travel type (LOCAL/INTERNATIONAL).") @RequestParam(required = false) TourType travelType,
            @Parameter(description = "Filter by requester's employee code.") @RequestParam(required = false) String cardNo,
            @Parameter(description = "Filter by requester's department.") @RequestParam(required = false) Long departmentId,
            @Parameter(hidden = true) Pageable pageable) {
        return service.list(status, travelType, cardNo, departmentId, PageableDefaults.apply(pageable, "tourStart"));
    }
}
