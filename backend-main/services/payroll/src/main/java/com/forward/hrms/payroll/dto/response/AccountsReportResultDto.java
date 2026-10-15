package com.forward.hrms.payroll.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * One page of an accounts report. {@code totals} and {@code totalEmployees}
 * cover the whole filtered result, not just this page, so they match the
 * export for the same filters.
 */
@Schema(name = "AccountsReportResult", description = "Rows, grand totals and paging for one accounts report run.")
public record AccountsReportResultDto(
        @Schema(description = "Legacy report id.", example = "C2") String reportId,
        @Schema(description = "Report title.") String title,
        @Schema(description = "Applied filters in readable form.",
                example = "Status: Active | Month: September 2026") String appliedFilters,
        @Schema(description = "Columns in display order.") List<AccountsReportColumnDto> columns,
        @Schema(description = "Ledger header (employee details) when the result is one employee's ledger; "
                + "null otherwise.", nullable = true) Map<String, Object> header,
        @Schema(description = "This page's rows, keyed by column key.") List<Map<String, Object>> rows,
        @Schema(description = "Grand total per totalable column key, over the full filtered result.")
        Map<String, BigDecimal> totals,
        @Schema(description = "Distinct employees in the full filtered result.") long totalEmployees,
        @Schema(description = "Zero-based page.") int page,
        @Schema(description = "Page size.") int size,
        @Schema(description = "Rows in the full filtered result.") long totalElements,
        @Schema(description = "Pages at this size.") int totalPages,
        @Schema(description = "Grouped lists only (e.g. by bank): one entry per group in print order with "
                + "`group`, `employees`, `rows` and `totals` (per totalable column) over the full result. "
                + "Null for other reports.", nullable = true)
        List<Map<String, Object>> groups
) {}
