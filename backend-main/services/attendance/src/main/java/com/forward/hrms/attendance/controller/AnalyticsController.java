package com.forward.hrms.attendance.controller;

import com.forward.hrms.attendance.service.AttendanceAnalyticsService;
import com.forward.hrms.attendance.service.LeaveAnalyticsService;
import com.forward.hrms.commons.web.security.RequirePermission;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Attendance + leave analytics JSON. Powers the on-time / absenteeism /
 * OT / leave dashboards. Personal scorecard surfaces under the same
 * permission as other attendance.read flows so an ESS user can view
 * their own — gated server-side by {@link AttendanceAnalyticsService}'s
 * caller match (today simply trusts the path id; tighten when ESS lands).
 */
@Tag(name = "Attendance + leave analytics",
        description = "JSON aggregations for the /analytics dashboards. " +
                "Requires `attendance.analytics.read` (or `attendance.leaves.analytics.read` for leaves).")
@RestController
@RequestMapping("/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AttendanceAnalyticsService attendance;
    private final LeaveAnalyticsService leaves;

    @Operation(summary = "Monthly on-time % trend",
            description = "Per month: PRESENT count + on-time count + on-time%. " +
                    "Window 1..24 months; defaults to 12.")
    @GetMapping("/attendance/on-time-pct")
    @RequirePermission("attendance.analytics.read")
    public AttendanceAnalyticsService.OnTimeTrendDto onTime(
            @RequestParam(defaultValue = "12") int months) {
        return attendance.onTimePct(months);
    }

    @Operation(summary = "Monthly absenteeism % trend",
            description = "Per month: PRESENT+ABSENT+LEAVE denominator + ABSENT count + absenteeism%. " +
                    "Window 1..24 months; defaults to 12.")
    @GetMapping("/attendance/absenteeism-pct")
    @RequirePermission("attendance.analytics.read")
    public AttendanceAnalyticsService.AbsenteeismTrendDto absenteeism(
            @RequestParam(defaultValue = "12") int months) {
        return attendance.absenteeismPct(months);
    }

    @Operation(summary = "Monthly OT-hours trend",
            description = "Per month: total OT minutes + OT hours. " +
                    "Window 1..24 months; defaults to 12.")
    @GetMapping("/attendance/ot-trend")
    @RequirePermission("attendance.analytics.read")
    public AttendanceAnalyticsService.OtHoursTrendDto otTrend(
            @RequestParam(defaultValue = "12") int months) {
        return attendance.otHoursTrend(months);
    }

    @Operation(summary = "Per-employee punctuality scorecard",
            description = "Rolling N-day window. PRESENT / on-time / late / absent / leave " +
                    "counts plus on-time% and OT hours. Window 1..365; defaults to 90.")
    @GetMapping("/attendance/scorecard/{employeeId}")
    @RequirePermission("attendance.analytics.read")
    public AttendanceAnalyticsService.ScorecardDto scorecard(
            @Parameter(description = "Employee id (upstream system).") @PathVariable long employeeId,
            @RequestParam(defaultValue = "90") int days) {
        return attendance.scorecard(employeeId, days);
    }

    @Operation(summary = "Leave utilization for a year",
            description = "Per leave-type and overall: allocated (incl. carried) + used + pending + used%.")
    @GetMapping("/leaves/utilization")
    @RequirePermission("attendance.leaves.analytics.read")
    public LeaveAnalyticsService.UtilizationDto utilization(@RequestParam int year) {
        return leaves.utilization(year);
    }

    @Operation(summary = "Leave seasonality (monthly days taken)",
            description = "Per month: total leave days + breakdown by leave-type. " +
                    "Window 1..24 months; defaults to 12.")
    @GetMapping("/leaves/seasonality")
    @RequirePermission("attendance.leaves.analytics.read")
    public LeaveAnalyticsService.SeasonalityDto seasonality(
            @RequestParam(defaultValue = "12") int months) {
        return leaves.seasonality(months);
    }
}
