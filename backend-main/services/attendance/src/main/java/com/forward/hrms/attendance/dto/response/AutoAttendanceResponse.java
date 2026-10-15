package com.forward.hrms.attendance.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Schema(name = "AutoAttendanceResponse",
        description = "Per-day outcome of an auto-attendance run: the days that got their IN / OUT " +
                "punches, and the days left alone with why.")
public record AutoAttendanceResponse(
        @Schema(description = "Days filled, with the instants written; check-out can be the next calendar day.")
        List<Applied> applied,
        List<Skipped> skipped,
        @Schema(description = "The narration written to each applied day's record notes and punch reason.")
        String narration
) {

    @Schema(name = "AutoAttendanceResponse.Applied")
    public record Applied(LocalDate date, LocalDateTime checkIn, LocalDateTime checkOut) {}

    /**
     * Why a day was left alone: WEEKEND (Sunday), HOLIDAY, LEAVE (approved
     * leave), LOCKED (payroll period locked), HAS_PUNCHES (already punched
     * and `replaceExisting` is off), NO_SHIFT (no times given and no shift
     * that day), OUTSIDE_SHIFT_WINDOW (a time outside the day's shift
     * window, or out not after in). Future days are filled, not skipped.
     */
    @Schema(name = "AutoAttendanceResponse.Skipped")
    public record Skipped(LocalDate date, String reason) {}
}
