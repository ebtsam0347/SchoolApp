package com.forward.hrms.bff.service;

import com.forward.hrms.attendance.dto.response.WorkflowChainDto;
import com.forward.hrms.bff.client.AttendanceClient;
import com.forward.hrms.bff.client.EmployeeClient;
import com.forward.hrms.bff.client.PayrollClient;
import com.forward.hrms.bff.service.dashboard.DashboardSupport;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Composite for {@code GET /bff/approval-workflows}: attendance, employee,
 * and payroll each mount the same commons-approval {@code /approval-workflows}
 * controller, so no single service knows the full catalog of request types.
 * Fans out to all three in parallel and concatenates — each source's own
 * {@code listChains()} already covers only the request types it registers,
 * so there's no overlap to merge.
 */
@Service
@RequiredArgsConstructor
public class ApprovalWorkflowCompositeService {

    private final AttendanceClient attendance;
    private final EmployeeClient employees;
    private final PayrollClient payroll;
    private final DashboardSupport support;

    public List<WorkflowChainDto> list() {
        CompletableFuture<List<WorkflowChainDto>> attendanceF =
                support.supplyOrDefault(attendance::approvalWorkflows, List.of(), "attendance-approval-workflows");
        CompletableFuture<List<WorkflowChainDto>> employeeF =
                support.supplyOrDefault(employees::approvalWorkflows, List.of(), "employee-approval-workflows");
        CompletableFuture<List<WorkflowChainDto>> payrollF =
                support.supplyOrDefault(payroll::approvalWorkflows, List.of(), "payroll-approval-workflows");

        CompletableFuture.allOf(attendanceF, employeeF, payrollF).join();

        List<WorkflowChainDto> out = new ArrayList<>();
        out.addAll(attendanceF.join());
        out.addAll(employeeF.join());
        out.addAll(payrollF.join());
        return out;
    }
}
