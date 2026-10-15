package com.forward.hrms.attendance.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalTime;

@Schema(name = "UpdateShiftOverrideRequest",
        description = "Patch — null fields stay unchanged. A change to from/to recomputes the union of " +
                "old and new ranges.")
public record UpdateShiftOverrideRequest(
        @Schema(nullable = true) LocalTime shiftStartTime,
        @Schema(nullable = true) LocalTime shiftEndTime,
        @Schema(nullable = true) LocalTime shiftBreakStartTime,
        @Schema(nullable = true) LocalTime shiftBreakEndTime,
        @Schema(nullable = true) Integer shiftGraceMinutes,
        @Schema(nullable = true) LocalDate fromDate,
        @Schema(nullable = true) LocalDate toDate,
        @Schema(nullable = true) String reason
) {}
