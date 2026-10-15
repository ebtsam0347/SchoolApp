package com.forward.hrms.attendance.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.time.LocalDate;
import java.time.LocalTime;

@Schema(name = "UpdateCanteenAttendanceRequest",
        description = "Patch — null fields stay unchanged. The employee on an entry is immutable; " +
                "delete and re-add if the wrong employee was recorded.")
public record UpdateCanteenAttendanceRequest(
        @Schema(nullable = true, example = "13:05:00") LocalTime attendanceTime,
        @Schema(description = "Meals this entry covers. Allowed range is 1 to 5.",
                nullable = true, example = "1")
        @Min(1) @Max(5) Integer count
) {}
