package com.forward.hrms.attendance.dto.request;

import com.forward.hrms.attendance.enums.HolidayType;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "UpdateHolidayRequest",
        description = "Patch a holiday. Null fields stay unchanged. The date is immutable — " +
                "delete and recreate if the date itself is wrong.")
public record UpdateHolidayRequest(
        @Schema(nullable = true) String name,
        @Schema(nullable = true) HolidayType type,
        @Schema(nullable = true) String description
) {}
