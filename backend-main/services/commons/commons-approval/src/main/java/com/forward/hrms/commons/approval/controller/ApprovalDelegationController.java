package com.forward.hrms.commons.approval.controller;

import com.forward.hrms.commons.approval.autoconfig.ApprovalPermissions;
import com.forward.hrms.commons.approval.dto.request.CreateDelegationOnBehalfRequest;
import com.forward.hrms.commons.approval.dto.request.CreateDelegationRequest;
import com.forward.hrms.commons.approval.dto.request.UpdateDelegationRequest;
import com.forward.hrms.commons.approval.dto.response.DelegationDto;
import com.forward.hrms.commons.approval.enums.DelegationStatus;
import com.forward.hrms.commons.approval.service.ApprovalDelegationService;
import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.ForbiddenError;
import com.forward.hrms.commons.core.error.UnauthorizedError;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "Approval delegations",
        description = "Lets a manager hand approval authority to another user over a date range. " +
                "Resolution is dynamic — assigned_to on approval_steps is never rewritten.")
@RestController
@RequestMapping("/approval-delegations")
@RequiredArgsConstructor
public class ApprovalDelegationController {

    private final ApprovalDelegationService delegations;
    private final ApprovalPermissions permissions;

    @Operation(summary = "List delegations involving the caller")
    @GetMapping("/me")
    public List<DelegationDto> myDelegations(
            @RequestParam(required = false) DelegationStatus status,
            @RequestParam(required = false) LocalDate activeOn) {
        requirePermission(permissions.read());
        return delegations.myDelegations(status, activeOn);
    }

    @Operation(summary = "Create a delegation (caller as delegator)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DelegationDto create(@Valid @RequestBody CreateDelegationRequest req) {
        requirePermission(permissions.write());
        return delegations.create(req);
    }

    @Operation(summary = "Create a delegation on a manager's behalf (HR)")
    @PostMapping("/on-behalf")
    @ResponseStatus(HttpStatus.CREATED)
    public DelegationDto createOnBehalf(@Valid @RequestBody CreateDelegationOnBehalfRequest req) {
        requirePermission(permissions.override());
        return delegations.createOnBehalf(req);
    }

    @Operation(summary = "Patch (extend / shorten / annotate) a delegation")
    @PatchMapping("/{id}")
    public DelegationDto update(@PathVariable long id, @Valid @RequestBody UpdateDelegationRequest req) {
        requirePermission(permissions.write());
        return delegations.update(id, req);
    }

    @Operation(summary = "Revoke a delegation")
    @DeleteMapping("/{id}")
    public DelegationDto revoke(@PathVariable long id) {
        requirePermission(permissions.write());
        return delegations.revoke(id);
    }

    private static void requirePermission(String permission) {
        CurrentUser user = CurrentUserHolder.get()
                .orElseThrow(() -> new UnauthorizedError("Authentication required."));
        if (!user.hasPermission(permission)) {
            throw new ForbiddenError("Missing required permission: " + permission);
        }
    }
}