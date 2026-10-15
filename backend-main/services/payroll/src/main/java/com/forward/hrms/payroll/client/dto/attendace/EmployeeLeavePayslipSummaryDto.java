package com.forward.hrms.payroll.client.dto.attendace;

import java.util.List;

/** Mirrors attendance-service's {@code EmployeeLeavePayslipSummaryDto}. */
public record EmployeeLeavePayslipSummaryDto(
        long employeeId,
        List<LeaveTypeAvailedDto> leaves,
        AnnualLeaveSummaryDto annualLeave,
        PayrollAttendanceAuditDto attendance
) {}
