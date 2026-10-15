package com.forward.hrms.payroll.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(name = "EmployeeSalaryDto",
        description = "Effective-dated basic-salary row. Open-ended (effective_to=null) row is current.")
public record EmployeeSalaryDto(
        long id,
        long employeeId,
        @Schema(description = "Monthly basic in rupees.") BigDecimal basic,
        LocalDate effectiveFrom,
        @Schema(nullable = true) LocalDate effectiveTo,
        @Schema(nullable = true) String revisionReason
) {}
