package com.forward.hrms.attendance.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.time.LocalDate;

@Schema(name = "PunchDto")
public record PunchDto(
        long id,
        long employeeId,
        LocalDateTime punchedAt,
        LocalDate workDate,
        String source,
        @Schema(nullable = true, description = "FK into attendance_devices.id") Long deviceId,
        @Schema(nullable = true) String reason,
        @Schema(nullable = true, description = "IN / OUT when an operator pinned this punch as the " +
                "day's check-in / check-out; null when derived by position.") String direction
) {}
