package com.forward.hrms.payroll.dto.response;


import java.util.List;
import java.util.Map;

public record AnnualLeaveCalculateResponse(
        Integer year,
        Integer month,

        int totalEmployeesProcessed,
        int successCount,
        int failedCount,

        List<AnnualLeaveEmployeeProcessResult> successfulEmployees,
        List<AnnualLeaveEmployeeProcessResult> failedEmployees
) {}