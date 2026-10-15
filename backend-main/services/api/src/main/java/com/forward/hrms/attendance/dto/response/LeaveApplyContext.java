package com.forward.hrms.attendance.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "LeaveApplyContext",
        description = "One-shot bundle for the apply-leave drawer — all three reads the drawer " +
                "needs on open. Avoids the cold-start jitter of three sequential calls. Holidays " +
                "are bounded to the next 90 days from today (Asia/Karachi); past holidays are " +
                "elsewhere on the calendar view.")
public record LeaveApplyContext(
        @Schema(description = "Active leave types from the catalog.")
        List<LeaveTypeDto> types,
        @Schema(description = "Caller's current-year balances, denormalized with leave type code.")
        List<LeaveBalanceDto> balances,
        @Schema(description = "Holidays in the next 90 days from today (Asia/Karachi).")
        List<HolidayDto> holidaysInNext90Days
) {}
