package com.forward.hrms.attendance.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(name = "HolidayDto")
public record HolidayDto(
        long id,
        LocalDate holidayDate,
        String name,
        String type,
        @Schema(nullable = true) String description
) {}
