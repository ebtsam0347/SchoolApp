package com.forward.hrms.attendance.dto.response;

import com.forward.hrms.employee.dto.response.EmployeeSummary;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

@Schema(name = "OperatorDashboard",
        description = "One-shot daily view for the attendance-operator persona. Headlines + the two " +
                "lists the operator most often acts on (missing checkouts, late arrivals) plus volume " +
                "stats (today's punches / overrides / pending regularizations). Underlying calls all " +
                "fan out in parallel; permission gating is on the upstream calls. Default date is " +
                "today (server-local Asia/Karachi).")
public record OperatorDashboard(
        @Schema(description = "The date this dashboard reflects.") LocalDate date,
        @Schema(description = "Status counts (PRESENT/LATE/ABSENT/...) for the date.")
        OrgPulseDto pulse,
        @Schema(description = "Total punches recorded for this date — across every source.")
        long totalPunchesToday,
        @Schema(description = "Number of shift overrides whose range covers this date.")
        int activeOverridesToday,
        @Schema(description = "Open regularization requests pending approval (org-wide count).")
        int pendingRegularizations,
        @Schema(description = "Employees who punched in but haven't punched out yet — primary " +
                "operator action queue. Capped to keep the dashboard payload bounded.")
        List<AttendanceRow> missingCheckouts,
        @Schema(description = "Employees with late_minutes > 0 today.")
        List<AttendanceRow> lateArrivals
) {

    @Schema(name = "OperatorDashboard.AttendanceRow",
            description = "Daily attendance record + the compact employee summary used everywhere.")
    public record AttendanceRow(
            AttendanceRecordDto record,
            @Schema(nullable = true) EmployeeSummary employee) {}
}
