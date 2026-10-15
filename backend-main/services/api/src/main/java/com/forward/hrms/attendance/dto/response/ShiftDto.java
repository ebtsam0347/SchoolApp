package com.forward.hrms.attendance.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalTime;

@Schema(name = "ShiftDto")
public record ShiftDto(
        long id,
        String code,
        String name,
        LocalTime startTime,
        LocalTime endTime,
        @Schema(nullable = true) LocalTime breakStartTime,
        @Schema(nullable = true) LocalTime breakEndTime,
        @Schema(description = "Derived from break window.") int breakMinutes,
        int graceMinutes,
        @Schema(nullable = true) Integer fridayExtraMinutes,
        boolean active,
        @Schema(description = "HR-controlled sort order; lower sorts first.", example = "10") int displayOrder,
        @Schema(description = "Day or night shift.", allowableValues = {"DAY", "NIGHT"}) String period
) {}
