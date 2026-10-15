package com.forward.hrms.bff.controller;

import com.forward.hrms.attendance.dto.response.WorkflowChainDto;
import com.forward.hrms.bff.service.ApprovalWorkflowCompositeService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Approvals — Composites",
        description = "Combined approval-chain catalog across attendance, employee, and payroll — each " +
                "mounts the same commons-approval /approval-workflows controller, so no single service can " +
                "list every request type on its own.")
@RestController
@RequestMapping("/bff/approval-workflows")
@RequiredArgsConstructor
public class ApprovalWorkflowsController {

    private final ApprovalWorkflowCompositeService composite;

    @Operation(summary = "List the configured chain for every request type across all services")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<WorkflowChainDto> list() {
        return composite.list();
    }
}
