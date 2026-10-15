package com.forward.hrms.attendance.dto.response;

import com.forward.hrms.employee.dto.response.EmployeeSummary;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(name = "DepartmentAttendanceRow",
        description = "One employee of the caller's department on the requested date — same row " +
                "shape as the manager's `/bff/attendance/team/today`: the employee summary plus " +
                "status, punch times and lateness.")
public record DepartmentAttendanceRow(
        EmployeeSummary employee,
        @Schema(description = "Attendance status — PRESENT, ABSENT, LEAVE, HOLIDAY, WEEKEND, " +
                "HALF_DAY, or PENDING_PUNCH when no record exists yet for the date.")
        String status,
        @Schema(nullable = true) LocalDateTime checkIn,
        @Schema(nullable = true) LocalDateTime checkOut,
        int lateMinutes
) {}
