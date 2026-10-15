package com.forward.hrms.attendance.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

@Schema(name = "LeavePreviewDto",
        description = "Dry-run shape for the apply-leave drawer. Same business rules submit enforces — " +
                "Sundays + holidays are skipped, half-day collapses to 0.5, balance projection uses " +
                "current balances. `warnings[]` carries soft hints (e.g. NEGATIVE_BALANCE, " +
                "CROSSES_FY) that the FE can surface without blocking submit; the actual submit will " +
                "still fail hard if a rule is violated.")
public record LeavePreviewDto(
        @Schema(description = "Net working days that count toward the request.")
        BigDecimal workingDays,
        int sundaysSkipped,
        int holidaysSkipped,
        @Schema(description = "Caller's available balance for this leave type after the request lands.",
                nullable = true)
        BigDecimal balanceAfter,
        @Schema(description = "Soft warnings; submit will still attempt with these set.")
        List<String> warnings
) {}
