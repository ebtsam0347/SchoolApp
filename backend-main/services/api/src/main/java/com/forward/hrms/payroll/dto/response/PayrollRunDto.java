package com.forward.hrms.payroll.dto.response;

import com.forward.hrms.payroll.enums.PayrollRunStatus;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(name = "PayrollRunDto")
public record PayrollRunDto(
        long id,
        String fiscalYearCode,
        int periodYear,
        int periodMonth,
        PayrollRunStatus status,
        boolean attendanceLocked,
        @Schema(nullable = true) LocalDateTime computedAt,
        @Schema(nullable = true) LocalDateTime approvedAt,
        @Schema(nullable = true) LocalDateTime postedAt,
        @Schema(nullable = true) Long approvedBy,
        @Schema(nullable = true) String notes,
        @Schema(description = "Count of employees compute could not produce a payslip for. " +
                "Drill-in via GET /payroll/runs/{id}/skips.")
        int skippedCount,
        @Schema(description = "Rows processed by the currently-running step (compute or post). " +
                "Updated every ~25 rows. Reset to 0 between steps and on /reset.")
        int progressCount,
        @Schema(description = "Total candidates for the currently-running step. Set at saga kickoff: " +
                "active payroll settings count for compute, unrendered payslip count for post. " +
                "FE renders progressCount / totalCount.")
        int totalCount
) {}
