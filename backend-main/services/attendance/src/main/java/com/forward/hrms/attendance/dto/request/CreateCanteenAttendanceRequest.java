package com.forward.hrms.attendance.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

@Schema(name = "CreateCanteenAttendanceRequest",
        description = "Records one canteen meal entry manually. The employee must exist and be " +
                "ACTIVE. `attendanceDate` / `attendanceTime` default to now, which is the normal " +
                "counter flow (clerk types the employee id as the meal is served). No device_ip " +
                "here — that is filled by the canteen device poller when it ships.")
public record CreateCanteenAttendanceRequest(
        @Schema(description = "Employee-schema employee id.", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Long employeeId,

        @Schema(description = "Meal date. Defaults to today.", nullable = true)
        LocalDate attendanceDate,

        @Schema(description = "Meal time. Defaults to now (seconds precision).", nullable = true,
                example = "13:05:00")
        LocalTime attendanceTime,

        @Schema(description = "Meals this entry covers. Allowed range is 1 to 5. Defaults to 1.",
                nullable = true, example = "1")
        @Min(1) @Max(5) Integer count
) {}
