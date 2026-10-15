package com.forward.hrms.attendance.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "EmployeeTodayDto",
        description = "Per-employee 'today' projection. Always 200 — when no punches and no nightly close " +
                "have produced a record yet, state is NOT_PUNCHED and record is null.")
public record EmployeeTodayDto(
        @Schema(description = "PRESENT when a record exists; NOT_PUNCHED when no record exists yet.",
                allowableValues = {"PRESENT", "NOT_PUNCHED"})
        String state,
        @Schema(nullable = true, description = "Today's attendance record, or null when state == NOT_PUNCHED.")
        AttendanceRecordDto record
) {
    public static EmployeeTodayDto present(AttendanceRecordDto record) {
        return new EmployeeTodayDto("PRESENT", record);
    }

    public static EmployeeTodayDto notPunched() {
        return new EmployeeTodayDto("NOT_PUNCHED", null);
    }
}
