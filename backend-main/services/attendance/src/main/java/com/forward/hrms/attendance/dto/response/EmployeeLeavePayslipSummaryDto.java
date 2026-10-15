package com.forward.hrms.attendance.dto.response;

import java.util.List;

/**
 * One employee's payslip inputs from attendance-service for a payroll
 * period: the derived attendance classification (validation of what
 * payroll persisted), every paid leave type's month / cycle usage, and
 * annual leave's own block. Served in bulk by
 * {@code GET /leaves/employees/payslip-summary}.
 */
public record EmployeeLeavePayslipSummaryDto(

        long employeeId,

        List<LeaveTypeAvailedDto> leaves,

        AnnualLeaveSummaryDto annualLeave,

        PayrollAttendanceAuditDto attendance

) {}
