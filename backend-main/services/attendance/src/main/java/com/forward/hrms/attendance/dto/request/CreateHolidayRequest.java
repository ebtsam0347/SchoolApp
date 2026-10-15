package com.forward.hrms.attendance.dto.request;

import com.forward.hrms.attendance.enums.HolidayType;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

@Schema(name = "CreateHolidayRequest")
public record CreateHolidayRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalDate holidayDate,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull HolidayType type,
        @Schema(nullable = true) String description
) {}
