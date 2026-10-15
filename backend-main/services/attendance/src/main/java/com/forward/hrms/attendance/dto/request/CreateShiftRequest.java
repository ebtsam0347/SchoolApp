package com.forward.hrms.attendance.dto.request;

import com.forward.hrms.attendance.enums.ShiftPeriod;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;

@Schema(name = "CreateShiftRequest")
public record CreateShiftRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 32) String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank @Size(max = 64) String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalTime startTime,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalTime endTime,
        @Schema(nullable = true) LocalTime breakStartTime,
        @Schema(nullable = true) LocalTime breakEndTime,
        @Schema(description = "Default 10.") Integer graceMinutes,
        @Schema(description = "Extra minutes added to break + end on Fridays only. Null/0 = no Friday adjustment.")
        Integer fridayExtraMinutes,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Day or night shift.")
        @NotNull ShiftPeriod period
) {}
