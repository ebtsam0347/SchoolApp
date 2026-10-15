package com.forward.hrms.payroll.dto.response;

import com.forward.hrms.payroll.enums.AllowanceRunStatus;
import com.forward.hrms.payroll.enums.PayrollRunStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(name = "AllowanceRunDto")
public record AllowanceRunDto(
        long id,
        String fiscalYearCode,
        int periodYear,
        int periodMonth,
        AllowanceRunStatus status,
        @Schema(nullable = true) LocalDateTime computedAt,
        @Schema(nullable = true) LocalDateTime approvedAt,
        @Schema(nullable = true) LocalDateTime postedAt,
        @Schema(nullable = true) Long approvedBy,
        @Schema(nullable = true) String notes,
        @Schema(description = "")
        int skippedCount,
        @Schema(description = "Rows processed by the currently-running step (compute or post). " +
                "Updated every ~25 rows. Reset to 0 between steps and on /reset.")
        int progressCount,
        @Schema(description = "Total candidates for the currently-running step. Set at saga kickoff")
        int totalCount
) {}
