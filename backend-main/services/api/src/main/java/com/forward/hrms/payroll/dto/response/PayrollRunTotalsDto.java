package com.forward.hrms.payroll.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Pre-aggregated totals for a payroll run. Replaces the BFF dashboard
 * pattern of fetching every payslip just to sum it.
 */
@Schema(description = "Pre-aggregated totals for a payroll run, optionally filtered by status / employeeCode substring.")
public record PayrollRunTotalsDto(
        @Schema(description = "Run id.") long runId,
        @Schema(description = "Number of payslips matching the filter.") long employeeCount,
        @Schema(description = "Sum of basic.") BigDecimal basic,
        @Schema(description = "Sum of allowances.") BigDecimal allowances,
        @Schema(description = "Sum of gross.") BigDecimal gross,
        @Schema(description = "Sum of non-tax deductions.") BigDecimal deductions,
        @Schema(description = "Sum of income tax.") BigDecimal tax,
        @Schema(description = "Sum of EOBI deductions.") BigDecimal eobi,
        @Schema(description = "Sum of PESSI employer contributions (cost only, not deducted).") BigDecimal pessiEmployer,
        @Schema(description = "Sum of net.") BigDecimal net,
        @Schema(description = "Count of HELD payslips within the filter.") long heldCount,
        @Schema(description = "Count of RELEASE_PENDING payslips within the filter.") long releasePendingCount
) {}
