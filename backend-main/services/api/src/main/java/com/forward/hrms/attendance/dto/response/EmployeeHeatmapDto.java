package com.forward.hrms.attendance.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

@Schema(name = "EmployeeHeatmapDto",
        description = "Per-day status + late-variance for an employee in a given month. Server-" +
                "side rollup so the FE doesn't have to walk per-day attendance rows just to " +
                "render the heatmap (D-S5 in BFF_WISHLIST.md).")
public record EmployeeHeatmapDto(
        long employeeId,
        @Schema(example = "2026-04") String month,
        List<Day> days
) {
    @Schema(name = "EmployeeHeatmapDto.Day")
    public record Day(
            LocalDate date,
            @Schema(description = "PRESENT | LATE | ABSENT | LEAVE | HALF_DAY | HOLIDAY | WEEKEND.")
            String status,
            @Schema(description = "Late minutes the day variance bar shows; 0 for non-PRESENT days.")
            int varianceMin
    ) {}
}
