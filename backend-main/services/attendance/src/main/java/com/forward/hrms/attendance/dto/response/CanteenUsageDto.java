package com.forward.hrms.attendance.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "CanteenUsageDto",
        description = "Per-employee canteen usage over a date range, derived from CANTEEN-typed " +
                "device events. `days` is the count of distinct calendar dates; `weeks` is the " +
                "count of distinct 7-day chunks measured from the 1st of the month " +
                "(days 1–7, 8–14, 15–21, 22–28, 29–end).")
public record CanteenUsageDto(
        @Schema(description = "Distinct dates with at least one canteen punch.", example = "12")
        int days,
        @Schema(description = "Distinct 7-day chunks (from day 1) with at least one canteen punch.",
                example = "3")
        int weeks
) {}
