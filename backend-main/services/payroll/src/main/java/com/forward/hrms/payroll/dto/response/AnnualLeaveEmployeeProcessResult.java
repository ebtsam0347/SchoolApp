package com.forward.hrms.payroll.dto.response;

public record AnnualLeaveEmployeeProcessResult(
        Long employeeId,
        String cardNo,
        String status,      // SUCCESS or FAILED
        String message
) {
}
