package com.forward.hrms.payroll.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@Schema(name = "AllowanceHistoryEntryDto",
        description = "One version of an employee's allowance set — the amounts in force after one save, until the next.")
public record AllowanceHistoryEntryDto(
        @Schema(description = "Day of the save that started this version.") LocalDate effectiveFrom,
        @Schema(description = "Day of the save that closed this version; null on the current (active) one.", nullable = true)
        LocalDate effectiveTo,
        @Schema(description = "Monthly rupees keyed by component code.") Map<String, BigDecimal> amounts,
        @Schema(description = "Sum of `amounts`.") BigDecimal total,
        @Schema(description = "Username that started this version.", nullable = true) String createdBy,
        @Schema(description = "When this version was started.") LocalDateTime createdAt,
        @Schema(description = "Username that closed this version.", nullable = true) String updatedBy,
        @Schema(description = "When this version was closed.", nullable = true) LocalDateTime updatedAt
) {}
