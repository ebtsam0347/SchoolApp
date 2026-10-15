package com.forward.hrms.bff.dto.response;

import com.forward.hrms.employee.dto.response.EmployeeSummary;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;

@Schema(name = "TeamAttendanceToday",
        description = "Roll-call view for a manager's team on a given date. Each row carries the " +
                "report's employee summary plus the attendance signal we already collect — status, " +
                "punch times, lateness. Active-leave overlay is stubbed (always null) until an " +
                "employee-scoped APPROVED-leave endpoint lands upstream; the FE can still flag " +
                "leave days using `status === 'LEAVE'` from the record.")
public record TeamAttendanceToday(
        @Schema(description = "Date the rows describe (defaults to today, Asia/Karachi).")
        LocalDate asOf,
        @Schema(description = "One row per covered employee. Order = direct reports first, then " +
                "delegated reports. Capped at 500 — beyond that the response is truncated and " +
                "`truncated=true`.")
        List<Row> rows,
        @Schema(description = "True when the coverage set exceeded the 500-row cap and was truncated.")
        boolean truncated
) {
    @Schema(name = "TeamAttendanceToday.Row")
    public record Row(
            EmployeeSummary employee,
            @Schema(description = "Attendance status — PRESENT, ABSENT, LEAVE, HOLIDAY, WEEKEND, " +
                    "HALF_DAY, or PENDING_PUNCH when no record exists yet for the date.")
            String status,
            @Schema(nullable = true) LocalDateTime checkIn,
            @Schema(nullable = true) LocalDateTime checkOut,
            int lateMinutes,
            @Schema(nullable = true,
                    description = "Reserved for an active-leave overlay; always null in v1.")
            ActiveLeave activeLeave
    ) {}

    @Schema(name = "TeamAttendanceToday.ActiveLeave")
    public record ActiveLeave(String leaveTypeCode, boolean halfDay) {}
}
