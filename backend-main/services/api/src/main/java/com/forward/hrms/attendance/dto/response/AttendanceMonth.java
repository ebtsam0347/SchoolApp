package com.forward.hrms.attendance.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

@Schema(name = "AttendanceMonth",
        description = "Calendar month view. Each day's `kind` is the FE's render hint — pick the " +
                "single overlay that wins for that cell. Precedence (high → low): " +
                "HOLIDAY > LEAVE > the record's status (PRESENT, ABSENT, HALF_DAY, WEEKEND).")
public record AttendanceMonth(
        @Schema(description = "Per-day rows for the month, in chronological order.")
        List<Day> days,
        Summary summary
) {
    @Schema(name = "AttendanceMonth.Day")
    public record Day(
            LocalDate workDate,
            @Schema(description = "Render hint — one of PRESENT, ABSENT, LEAVE, HOLIDAY, WEEKEND, HALF_DAY.")
            String kind,
            @Schema(nullable = true) AttendanceRecordDto record,
            @Schema(nullable = true) HolidayDto holiday,
            @Schema(nullable = true) DayLeave leave
    ) {}

    @Schema(name = "AttendanceMonth.DayLeave",
            description = "Compact leave overlay — full request lives elsewhere.")
    public record DayLeave(
            long requestId,
            String leaveTypeCode,
            boolean halfDay,
            @Schema(nullable = true) String halfDayPart
    ) {}

    @Schema(name = "AttendanceMonth.Summary")
    public record Summary(
            int present,
            int absent,
            int leave,
            int holiday,
            int halfDay,
            int weekend,
            int totalWorkedMinutes,
            int totalOtMinutes,
            int totalLateMinutes
    ) {}
}
