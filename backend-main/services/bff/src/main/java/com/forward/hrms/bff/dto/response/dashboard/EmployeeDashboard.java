package com.forward.hrms.bff.dto.response.dashboard;

import com.forward.hrms.attendance.dto.response.AttendanceRecordDto;
import com.forward.hrms.attendance.dto.response.LeaveRequestDto;
import com.forward.hrms.attendance.dto.response.HolidayDto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Schema(name = "EmployeeDashboard",
        description = "Composite payload for the /dashboard route when the caller is rendered as " +
                "the Employee persona. Replaces ~6 fan-outs (employee me, payslips, leave " +
                "balance, attendance month, holidays, notifications).")
public record EmployeeDashboard(
        @Schema(nullable = true) NextPayday nextPayday,
        Today today,
        NeedsYou needsYou,
        LeaveBalance leaveBalance,
        AttendanceHeatmap attendanceHeatmap,
        Payslips payslips,
        List<UpcomingHoliday> upcomingHolidays
) {
    @Schema(name = "EmployeeDashboard.NextPayday",
            description = "Most recent payslip + the payroll run it belongs to. The FE displays " +
                    "this as the projected next payday — the run lifecycle (DRAFT / LOCKED / " +
                    "POSTED) tells the user whether numbers are final.")
    public record NextPayday(
            @Schema(nullable = true) LocalDate date,
            @Schema(nullable = true) Money projectedNet,
            @Schema(nullable = true) Long runId,
            @Schema(nullable = true) String runStatus
    ) {}

    @Schema(name = "EmployeeDashboard.Today",
            description = "Today's attendance status — pulled from this month's attendance roll-" +
                    "forward + today's punches.")
    public record Today(
            @Schema(description = "One of CHECKED_IN, ON_LEAVE, OFF, PENDING_PUNCH, HOLIDAY, " +
                    "WEEKEND, ABSENT.") String status,
            @Schema(nullable = true) LocalDateTime checkInAt,
            @Schema(nullable = true) Shift shift,
            @Schema(nullable = true) Integer elapsedMinutes
    ) {}

    @Schema(name = "EmployeeDashboard.Shift",
            description = "Assigned shift snapshot — null when the upstream attendance record " +
                    "doesn't carry one yet.")
    public record Shift(
            @Schema(nullable = true) String start,
            @Schema(nullable = true) String end,
            @Schema(nullable = true) String name
    ) {}

    @Schema(name = "EmployeeDashboard.NeedsYou",
            description = "Action queues. Each carries a count for the badge plus the top 5 rows " +
                    "the FE renders inline; \"see all\" links into the module page.")
    public record NeedsYou(
            CountedRequests pendingRequests,
            CountedPunches missingPunches,
            CountedDocs documentsToAck
    ) {}

    @Schema(name = "EmployeeDashboard.CountedRequests")
    public record CountedRequests(int count, List<RequestRow> items) {}

    @Schema(name = "EmployeeDashboard.RequestRow",
            description = "Pending leave / overtime / regularization request the caller submitted.")
    public record RequestRow(
            String kind,
            long id,
            String summary,
            String status,
            LocalDateTime createdAt
    ) {}

    @Schema(name = "EmployeeDashboard.CountedPunches")
    public record CountedPunches(int count, List<PunchRow> items) {}

    @Schema(name = "EmployeeDashboard.PunchRow",
            description = "Workday with no attendance record (no punches and the nightly close " +
                    "didn't leave a stub). The FE deep-links into the missing-punch flow.")
    public record PunchRow(LocalDate date, String reason) {}

    @Schema(name = "EmployeeDashboard.CountedDocs")
    public record CountedDocs(int count, List<DocRow> items) {}

    @Schema(name = "EmployeeDashboard.DocRow",
            description = "Doc the employee still needs to acknowledge. Empty for v1 — no " +
                    "downstream contract yet.")
    public record DocRow(long id, String name, String kind) {}

    @Schema(name = "EmployeeDashboard.LeaveBalance")
    public record LeaveBalance(LocalDate asOf, List<LeaveTypeBalance> types) {}

    @Schema(name = "EmployeeDashboard.LeaveTypeBalance")
    public record LeaveTypeBalance(
            String code,
            String label,
            BigDecimal used,
            BigDecimal pending,
            BigDecimal remaining,
            BigDecimal total
    ) {}

    @Schema(name = "EmployeeDashboard.AttendanceHeatmap",
            description = "Per-day status for the current month — drives the heatmap viz.")
    public record AttendanceHeatmap(String month, List<HeatmapDay> days) {}

    @Schema(name = "EmployeeDashboard.HeatmapDay")
    public record HeatmapDay(LocalDate date, String status, int varianceMin) {}

    @Schema(name = "EmployeeDashboard.Payslips")
    public record Payslips(List<PayslipRow> recent, List<NetTrendPoint> netTrend12m) {}

    @Schema(name = "EmployeeDashboard.PayslipRow",
            description = "Compact payslip for the dashboard list. Full detail (lines, PDF) lives " +
                    "on /bff/payslips/me.")
    public record PayslipRow(
            long id,
            int periodYear,
            int periodMonth,
            Money gross,
            Money net,
            String status,
            @Schema(nullable = true) LocalDateTime generatedAt
    ) {}

    @Schema(name = "EmployeeDashboard.NetTrendPoint")
    public record NetTrendPoint(String period, BigDecimal amount) {}

    @Schema(name = "EmployeeDashboard.UpcomingHoliday")
    public record UpcomingHoliday(LocalDate date, String name, String dayOfWeek) {}

    public static Today derive(LocalDate todayDate,
                                AttendanceRecordDto todayRec,
                                LeaveRequestDto activeLeave,
                                HolidayDto todayHoliday,
                                Map<Long, String> shiftNamesById) {
        Shift shift = shiftOf(todayRec, shiftNamesById);
        if (todayHoliday != null) return new Today("HOLIDAY", null, shift, null);
        if (activeLeave != null) return new Today("ON_LEAVE", null, shift, null);
        if (todayRec == null) {
            return new Today(isWeekend(todayDate) ? "OFF" : "PENDING_PUNCH",
                    null, null, null);
        }
        if (todayRec.checkIn() != null && todayRec.checkOut() == null) {
            int elapsed = (int) Math.max(0,
                    java.time.Duration.between(todayRec.checkIn(), LocalDateTime.now()).toMinutes());
            return new Today("CHECKED_IN", todayRec.checkIn(), shift, elapsed);
        }
        return new Today(todayRec.status(), todayRec.checkIn(), shift, todayRec.workedMinutes());
    }

    private static Shift shiftOf(AttendanceRecordDto rec, Map<Long, String> shiftNamesById) {
        if (rec == null) return null;
        if (rec.shiftStartTime() == null && rec.shiftEndTime() == null && rec.effectiveShift() == null) {
            return null;
        }
        String name = rec.effectiveShift() == null || shiftNamesById == null
                ? null
                : shiftNamesById.get(rec.effectiveShift());
        return new Shift(
                rec.shiftStartTime() == null ? null : rec.shiftStartTime().toString(),
                rec.shiftEndTime() == null ? null : rec.shiftEndTime().toString(),
                name);
    }

    private static boolean isWeekend(LocalDate d) {
        return d.getDayOfWeek() == java.time.DayOfWeek.SUNDAY
                || d.getDayOfWeek() == java.time.DayOfWeek.SATURDAY;
    }
}
