package com.forward.hrms.attendance.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalTime;

@Schema(name = "ShiftOverrideDto")
public record ShiftOverrideDto(
        long id,
        long employeeId,
        LocalDate fromDate,
        LocalDate toDate,
        LocalTime shiftStartTime,
        LocalTime shiftEndTime,
        LocalTime shiftBreakStartTime,
        LocalTime shiftBreakEndTime,
        @Schema(description = "Derived from break window.") int shiftBreakMinutes,
        int shiftGraceMinutes,
        @Schema(nullable = true) String reason
) {}
