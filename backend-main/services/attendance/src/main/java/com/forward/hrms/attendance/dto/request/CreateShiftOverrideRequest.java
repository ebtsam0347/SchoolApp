package com.forward.hrms.attendance.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Schema(name = "CreateShiftOverrideRequest",
        description = "Operator override over a date range, applied per employee. Triggers same-tx " +
                "recompute of every attendance_records row in the range.")
public record CreateShiftOverrideRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotEmpty List<Long> employeeIds,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalDate fromDate,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalDate toDate,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalTime shiftStartTime,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalTime shiftEndTime,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalTime shiftBreakStartTime,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalTime shiftBreakEndTime,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Integer shiftGraceMinutes,
        @Schema(nullable = true) String reason
) {}
