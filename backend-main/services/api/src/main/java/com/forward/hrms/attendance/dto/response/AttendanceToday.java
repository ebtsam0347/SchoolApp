package com.forward.hrms.attendance.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Schema(name = "AttendanceToday",
        description = "One-shot Today screen. Replaces 4–5 round-trips: today's attendance record, " +
                "today's punches, any active approved leave that overlaps today, today's holiday, " +
                "next upcoming holiday/leave, and a 3-bucket balance summary.")
public record AttendanceToday(
        @Schema(nullable = true, description = "Today's projection. Null when the day hasn't " +
                "produced a record yet (no punches, nightly close not run).")
        TodayRecord today,
        @Schema(description = "Today's punches in chronological order.")
        List<PunchDto> punches,
        @Schema(nullable = true, description = "Approved leave covering today; null when no overlap.")
        LeaveRequestDto activeLeave,
        @Schema(nullable = true, description = "Today's holiday; null on working days.")
        HolidayDto todayHoliday,
        Upcoming upcoming,
        BalanceSummary balanceSummary
) {
    @Schema(name = "AttendanceToday.TodayRecord",
            description = "Subset of AttendanceRecordDto plus the shift snapshot — the FE needs both " +
                    "to render schedule + worked time on the same card.")
    public record TodayRecord(
            LocalDate workDate,
            String status,
            @Schema(nullable = true) LocalDateTime checkIn,
            @Schema(nullable = true) LocalDateTime checkOut,
            int lateMinutes,
            int workedMinutes,
            int otNormalMinutes,
            int otExtraMinutes,
            int otSpecialMinutes,
            @Schema(nullable = true) LocalTime shiftStartTime,
            @Schema(nullable = true) LocalTime shiftEndTime,
            @Schema(nullable = true) Integer shiftBreakMinutes
    ) {}

    @Schema(name = "AttendanceToday.Upcoming")
    public record Upcoming(
            @Schema(nullable = true) HolidayDto nextHoliday,
            @Schema(nullable = true) NextLeave nextApprovedLeave
    ) {}

    @Schema(name = "AttendanceToday.NextLeave")
    public record NextLeave(LocalDate fromDate, LocalDate toDate, String leaveTypeCode) {}

    @Schema(name = "AttendanceToday.BalanceSummary",
            description = "The three buckets the Today header shows. Codes match the " +
                    "leave-type catalog — anything else is rolled into the corresponding bucket " +
                    "by name match (ANNUAL/SICK/CASUAL).")
    public record BalanceSummary(
            BigDecimal annualAvailable,
            BigDecimal sickAvailable,
            BigDecimal casualAvailable
    ) {}
}
