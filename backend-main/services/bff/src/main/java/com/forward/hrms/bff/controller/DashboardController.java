package com.forward.hrms.bff.controller;

import com.forward.hrms.bff.dto.response.dashboard.AdminDashboard;
import com.forward.hrms.bff.dto.response.dashboard.EmployeeDashboard;
import com.forward.hrms.bff.dto.response.dashboard.HrDashboard;
import com.forward.hrms.bff.dto.response.dashboard.ManagerDashboard;
import com.forward.hrms.bff.dto.response.dashboard.PayrollDashboard;
import com.forward.hrms.bff.service.dashboard.AdminDashboardService;
import com.forward.hrms.bff.service.dashboard.EmployeeDashboardService;
import com.forward.hrms.bff.service.dashboard.HrDashboardService;
import com.forward.hrms.bff.service.dashboard.ManagerDashboardService;
import com.forward.hrms.bff.service.dashboard.PayrollDashboardService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * One endpoint per persona, each independently cacheable and shaped for
 * its own /dashboard layout. Persona resolution is client-side — the FE
 * picks which endpoint to call from {@code useAuth().user.permissions}.
 *
 * <p>Each endpoint enforces persona-appropriate scope downstream
 * (manager → must have direct reports, HR → must hold the relevant
 * read scopes). Missing scope surfaces as a 403 from the failing slice;
 * the FE falls back to the Employee dashboard.
 */
@Tag(name = "Dashboard composites",
        description = "Per-persona one-shot composites for the /dashboard route. Replaces 6+ "
                + "fan-outs the browser would otherwise issue per landing.")
@RestController
@RequestMapping("/bff/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final EmployeeDashboardService employeeService;
    private final ManagerDashboardService managerService;
    private final HrDashboardService hrService;
    private final PayrollDashboardService payrollService;
    private final AdminDashboardService adminService;

    @Operation(summary = "Employee dashboard composite",
            description = "Self-service landing payload: next payday, today's attendance, action "
                    + "queues, leave balance, attendance heatmap, recent payslips + 12m net trend, "
                    + "and the next three holidays. Replaces ~6 fan-outs.")
    @ApiResponse(responseCode = "200", description = "Composite payload.")
    @ApiResponse(responseCode = "401", description = "Missing identity headers.")
    @GetMapping("/employee")
    @PreAuthorize("isAuthenticated()")
    public EmployeeDashboard employee() {
        return employeeService.build();
    }

    @Operation(summary = "Manager dashboard composite",
            description = "Manager landing for the caller's direct reports: team today + 14d "
                    + "attendance, approval queue (top 5), leave calendar (next 30 days). Returns "
                    + "403 if the caller has no direct reports — the FE then falls back to the "
                    + "Employee dashboard.")
    @ApiResponse(responseCode = "200", description = "Composite payload.")
    @ApiResponse(responseCode = "403", description = "Caller has no direct reports.")
    @GetMapping("/manager")
    @PreAuthorize("isAuthenticated()")
    public ManagerDashboard manager() {
        return managerService.build();
    }

    @Operation(summary = "HR dashboard composite",
            description = "Org pulse + queues (onboarding stuck, contracts/probation expiring, "
                    + "disciplinary open) + recent audit anomalies. Most counters require "
                    + "`employee.*.read`; missing scope surfaces zeros rather than failing the "
                    + "whole call.")
    @GetMapping("/hr")
    @PreAuthorize("isAuthenticated()")
    public HrDashboard hr() {
        return hrService.build();
    }

    @Operation(summary = "Payroll dashboard composite",
            description = "Current run + 6m run trend + payslip queues. Requires "
                    + "`payroll.read` minimum.")
    @GetMapping("/payroll")
    @PreAuthorize("isAuthenticated()")
    public PayrollDashboard payroll() {
        return payrollService.build();
    }

    @Operation(summary = "Admin dashboard composite",
            description = "System health + recent role changes + locked accounts. Requires "
                    + "admin-tier scope; sections degrade quietly when the caller lacks "
                    + "audit.admin.")
    @GetMapping("/admin")
    @PreAuthorize("isAuthenticated()")
    public AdminDashboard admin() {
        return adminService.build();
    }
}
