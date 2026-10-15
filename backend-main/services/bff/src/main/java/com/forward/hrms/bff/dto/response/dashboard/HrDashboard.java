package com.forward.hrms.bff.dto.response.dashboard;

import com.forward.hrms.audit.dto.response.TimelineEntry;
import com.forward.hrms.employee.dto.response.EmployeeSummary;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

@Schema(name = "HrDashboard",
        description = "Composite for the HR persona. Org-wide totals are the headline; queues are " +
                "filtered by caller scope where relevant.")
public record HrDashboard(
        OrgPulse orgPulse,
        Attendance30d attendance30d,
        Headcount12m headcount12m,
        Queues queues,
        List<DepartmentLeave> departmentLeave30d,
        List<TimelineEntry> recentAuditAnomalies
) {
    @Schema(name = "HrDashboard.OrgPulse",
            description = "Today's headcount roll-up. Today's status counters fall back to zero " +
                    "until D-S2 (org-wide attendance pulse endpoint) ships.")
    public record OrgPulse(
            LocalDate asOf,
            long headcount,
            long presentToday,
            long lateToday,
            long onLeaveToday,
            long absentToday
    ) {}

    @Schema(name = "HrDashboard.Attendance30d",
            description = "Per-day attendance rate over the last 30 days. Empty until D-S2 lands.")
    public record Attendance30d(LocalDate from, LocalDate to, List<RateDay> days) {}

    @Schema(name = "HrDashboard.RateDay")
    public record RateDay(LocalDate date, double rate, boolean anomaly) {}

    @Schema(name = "HrDashboard.Headcount12m",
            description = "Joiners / leavers / end-of-month headcount over the trailing 12 months. " +
                    "Empty until employee-service exposes the monthly aggregate (out of v1 scope).")
    public record Headcount12m(List<HeadcountMonth> months) {}

    @Schema(name = "HrDashboard.HeadcountMonth")
    public record HeadcountMonth(String month, long joiners, long leavers, long endHeadcount) {}

    @Schema(name = "HrDashboard.Queues")
    public record Queues(
            QueueSection onboardingStuck,
            QueueSection contractsExpiring,
            QueueSection disciplinaryOpen,
            LeaveHotspots leaveHotspots
    ) {}

    @Schema(name = "HrDashboard.QueueSection")
    public record QueueSection(int count, List<QueueItem> items) {}

    @Schema(name = "HrDashboard.QueueItem",
            description = "Generic queue row — `kind` discriminates so the FE renders the correct " +
                    "drawer / link.")
    public record QueueItem(
            String kind,
            long id,
            EmployeeSummary employee,
            String summary,
            @Schema(nullable = true) LocalDate due
    ) {}

    @Schema(name = "HrDashboard.LeaveHotspots")
    public record LeaveHotspots(int count, List<LeaveHotspotRow> items) {}

    @Schema(name = "HrDashboard.LeaveHotspotRow",
            description = "Employee burning through annual leave. Empty until the per-employee " +
                    "leave-balance aggregate ships.")
    public record LeaveHotspotRow(
            long personId,
            String name,
            @Schema(nullable = true) String departmentName,
            double annualUsedPct,
            double remainingDays
    ) {}

    @Schema(name = "HrDashboard.DepartmentLeave")
    public record DepartmentLeave(long departmentId, String name, double leaveDays) {}
}
