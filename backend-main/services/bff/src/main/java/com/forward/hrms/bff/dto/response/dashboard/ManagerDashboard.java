package com.forward.hrms.bff.dto.response.dashboard;

import com.forward.hrms.employee.dto.response.EmployeeSummary;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Schema(name = "ManagerDashboard",
        description = "Composite for the Manager persona. Scope is the caller's direct reports.")
public record ManagerDashboard(
        Team team,
        Attendance14d attendance14d,
        ApprovalQueue approvalQueue,
        LeaveCalendar30d leaveCalendar30d,
        Context context
) {
    @Schema(name = "ManagerDashboard.Team",
            description = "Team roster: counts plus a flat member list for the avatar stack. " +
                    "Per-person status for today is served by /bff/attendance/team/today.")
    public record Team(int size, int directReports, List<EmployeeSummary> members) {}

    @Schema(name = "ManagerDashboard.Attendance14d",
            description = "Last 14 days of team attendance, bucketed per day.")
    public record Attendance14d(LocalDate from, LocalDate to, List<DayBucket> days) {}

    @Schema(name = "ManagerDashboard.DayBucket")
    public record DayBucket(LocalDate date, int present, int late, int absent, int leave) {

        public static final class Builder {
            private final LocalDate date;
            private int present, late, absent, leave;

            public Builder(LocalDate date) {
                this.date = date;
            }

            public Builder add(String status) {
                if (status == null) return this;
                switch (status) {
                    case "PRESENT" -> present++;
                    case "LATE" -> { late++; present++; }
                    case "ABSENT" -> absent++;
                    case "LEAVE", "ON_LEAVE", "HALF_DAY" -> leave++;
                    default -> { /* HOLIDAY / WEEKEND / etc — don't bucket */ }
                }
                return this;
            }

            public DayBucket build() {
                return new DayBucket(date, present, late, absent, leave);
            }
        }
    }

    @Schema(name = "ManagerDashboard.ApprovalQueue",
            description = "Pending approvals across leave / overtime / regularization, capped at " +
                    "the top 5 rows; \"see all\" links into /approvals/inbox.")
    public record ApprovalQueue(int totalPending, List<ApprovalRow> items, Velocity velocity) {}

    @Schema(name = "ManagerDashboard.ApprovalRow")
    public record ApprovalRow(
            String kind,
            long id,
            EmployeeSummary employee,
            String summary,
            LocalDateTime createdAt
    ) {}

    @Schema(name = "ManagerDashboard.Velocity",
            description = "Average minutes between request submission and the caller's decision " +
                    "over the last 7 days, plus a 7-element daily trend (oldest → today). Empty " +
                    "days surface as 0.")
    public record Velocity(int avgDecisionMinutes7d, List<Integer> trend7d) {}

    @Schema(name = "ManagerDashboard.LeaveCalendar30d",
            description = "Approved team leaves in the next 30 days. Empty until the team-leave " +
                    "list endpoint lands; for v1 the FE renders \"no upcoming leaves\".")
    public record LeaveCalendar30d(LocalDate from, LocalDate to, List<LeaveItem> items) {}

    @Schema(name = "ManagerDashboard.LeaveItem")
    public record LeaveItem(
            long personId,
            String personName,
            String leaveType,
            LocalDate start,
            LocalDate end
    ) {}

    @Schema(name = "ManagerDashboard.Context",
            description = "Punctuality + exception roll-ups over the last 7 days.")
    public record Context(Punctuality punctualityWeek, Exceptions exceptionsWeek) {}

    @Schema(name = "ManagerDashboard.Punctuality",
            description = "Average late minutes across direct reports who attended in the last " +
                    "7 days, plus a 7-element daily trend (oldest → today).")
    public record Punctuality(int avgVarianceMin, List<Integer> trend7d) {}

    @Schema(name = "ManagerDashboard.Exceptions",
            description = "Count of LATE + ABSENT records across direct reports in the last 7 days.")
    public record Exceptions(int count) {}
}
