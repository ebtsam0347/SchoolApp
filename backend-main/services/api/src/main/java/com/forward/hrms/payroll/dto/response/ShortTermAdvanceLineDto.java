package com.forward.hrms.payroll.dto.response;

import com.forward.hrms.employee.dto.response.EmployeeSummary;
import com.forward.hrms.payroll.enums.ShortTermAdvanceStatus;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(name = "ShortTermAdvanceLineDto")
public record ShortTermAdvanceLineDto(
        long id,
        long batchId,
        long employeeId,
        @Schema(description = "Employee code, name, department, section and designation, resolved from the " +
                "shared employee cache.", nullable = true)
        EmployeeSummary employee,
        BigDecimal amount,
        int targetYear,
        int targetMonth,
        ShortTermAdvanceStatus status,
        @Schema(nullable = true) String rejectionReason,
        @Schema(nullable = true) Long deductedInRunId
) {}
