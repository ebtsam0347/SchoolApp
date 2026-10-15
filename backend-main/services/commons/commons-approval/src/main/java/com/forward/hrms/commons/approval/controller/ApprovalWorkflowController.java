package com.forward.hrms.commons.approval.controller;

import com.forward.hrms.commons.approval.autoconfig.ApprovalPermissions;
import com.forward.hrms.commons.approval.dto.request.SetWorkflowChainRequest;
import com.forward.hrms.commons.approval.dto.response.WorkflowChainDto;
import com.forward.hrms.commons.approval.service.ApprovalWorkflowService;
import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.ForbiddenError;
import com.forward.hrms.commons.core.error.UnauthorizedError;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Approval workflows",
        description = "HR-configurable approval chains per request type. Each stage resolves its approver " +
                "by MANAGER (the submitter's manager, delegatable) or PERMISSION (a role-only pool gated by a " +
                "permission). Editing a chain never affects in-flight requests.")
@RestController
@RequestMapping("/approval-workflows")
@RequiredArgsConstructor
public class ApprovalWorkflowController {

    private final ApprovalWorkflowService workflows;
    private final ApprovalPermissions permissions;

    @Operation(summary = "List the configured chain for every request type")
    @GetMapping
    public List<WorkflowChainDto> list() {
        requirePermission(permissions.read());
        return workflows.listChains();
    }

    @Operation(summary = "Get the configured chain for one request type")
    @GetMapping("/{requestType}")
    public WorkflowChainDto get(@PathVariable String requestType) {
        requirePermission(permissions.read());
        return workflows.getChain(requestType);
    }

    @Operation(summary = "Replace the chain for a request type (HR)",
            description = "Overwrites the whole chain with the supplied ordered stages; stage numbers are " +
                    "assigned by position.")
    @ApiResponse(responseCode = "422", description = "A PERMISSION stage is missing its approvePermission.")
    @PutMapping("/{requestType}")
    public WorkflowChainDto replace(@PathVariable String requestType,
                                    @Valid @RequestBody SetWorkflowChainRequest req) {
        requirePermission(permissions.admin());
        return workflows.replaceChain(requestType, req);
    }

    private static void requirePermission(String permission) {
        CurrentUser user = CurrentUserHolder.get()
                .orElseThrow(() -> new UnauthorizedError("Authentication required."));
        if (!user.hasPermission(permission)) {
            throw new ForbiddenError("Missing required permission: " + permission);
        }
    }
}
