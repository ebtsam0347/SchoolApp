package com.forward.hrms.attendance.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "CanteenAttendanceRangeCountDto",
        description = "Per-employee canteen attendance totals over an inclusive date range.")
public record CanteenAttendanceRangeCountDto(
        @Schema(description = "Employee-schema employee id.", example = "101")
        Long employeeId,
        @Schema(description = "Human-readable employee code.", example = "FS-2026-0042")
        String employeeCode,
        @Schema(description = "Number of attendance rows in the range.", example = "12")
        Long attendanceEntryCount,
        @Schema(description = "Sum of meal counts across those rows.", example = "14")
        Long canteenMealCount
) {
}
