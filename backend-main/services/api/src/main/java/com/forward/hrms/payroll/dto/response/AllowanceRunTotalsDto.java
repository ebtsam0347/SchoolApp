package com.forward.hrms.payroll.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Pre-aggregated totals for a allowance run, optionally filtered by status / employeeCode substring.")
public record AllowanceRunTotalsDto(
        @Schema(description = "Run id.") long runId,
        @Schema(description = "Number of employee matching the filter.") long employeeCount,
        @Schema(description = "Sum of allowance.") BigDecimal allowance
) {
}
