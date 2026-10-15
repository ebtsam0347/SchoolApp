package com.forward.hrms.attendance.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;

@Schema(name = "AttendanceRecordDto",
        description = "Per-day attendance projection. Shift fields are the snapshot — they do NOT " +
                "change when the shift catalog or assignment changes.")
public record AttendanceRecordDto(
        long id,
        long employeeId,
        LocalDate workDate,
        @Schema(nullable = true) LocalDateTime checkIn,
        @Schema(nullable = true) LocalDateTime checkOut,
        @Schema(nullable = true) Long effectiveShift,
        @Schema(nullable = true) LocalTime shiftStartTime,
        @Schema(nullable = true) LocalTime shiftEndTime,
        @Schema(nullable = true, description = "workDate + shiftStartTime.")
        LocalDateTime shiftStartDateTime,
        @Schema(nullable = true, description = "workDate + shiftEndTime; next day when the shift crosses midnight.")
        LocalDateTime shiftEndDateTime,
        @Schema(nullable = true) LocalTime shiftBreakStartTime,
        @Schema(nullable = true) LocalTime shiftBreakEndTime,
        @Schema(description = "Derived from break window.") int shiftBreakMinutes,
        @Schema(nullable = true) Integer shiftGraceMinutes,
        String status,
        int lateMinutes,
        int earlyMinutes,
        int workedMinutes,
        int otNormalMinutes,
        int otExtraMinutes,
        int otSpecialMinutes,
        @Schema(nullable = true) String source,
        @Schema(nullable = true) String notes,
        @Schema(nullable = true,
                description = "§7.payroll #23 — paid-leave flag. Populated when status == LEAVE from the leave type's paid column at approval time. Null on every other status.")
        Boolean paid
) {}
