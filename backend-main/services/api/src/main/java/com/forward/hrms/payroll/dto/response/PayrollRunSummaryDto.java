package com.forward.hrms.payroll.dto.response;

import com.forward.hrms.payroll.enums.PayrollRunStatus;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * Live snapshot of a payroll run for FE polling. Bundles status,
 * mid-run counts, and SQL-aggregated payslip totals so the FE only
 * polls one URL during compute / post. {@code progressCount} and
 * {@code skippedCount} are pulsed every ~25 rows; the totals come
 * from a SUM over committed payslips so they move live too.
 */
@Schema(name = "PayrollRunSummaryDto",
        description = "One-shot polling shape: status + live counts + aggregated payslip totals.")
public record PayrollRunSummaryDto(
        long id,
        String fiscalYearCode,
        int periodYear,
        int periodMonth,
        PayrollRunStatus status,
        @Schema(nullable = true) LocalDateTime computedAt,
        @Schema(nullable = true) LocalDateTime postedAt,
        @Schema(description = "Total candidates for the currently-running step. Set at saga kickoff.")
        int totalCount,
        @Schema(description = "Rows processed so far (succeeded + skipped). Pulsed every ~25 rows.")
        int progressCount,
        @Schema(description = "Rows compute could not produce a payslip for so far. Pulsed every ~25 rows.")
        int skippedCount,
        @Schema(description = "processed minus skipped. Server-derived so the FE doesn't have to subtract.")
        int successCount,
        @Schema(description = "Pre-aggregated payslip sums (basic, allowances, gross, deductions, " +
                "tax, eobi, pessiEmployer, net) plus HELD / RELEASE_PENDING counts. Live during " +
                "compute because payslips commit per-row.")
        PayrollRunTotalsDto totals
) {}
