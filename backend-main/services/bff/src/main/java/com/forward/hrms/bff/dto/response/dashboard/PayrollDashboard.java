package com.forward.hrms.bff.dto.response.dashboard;

import com.forward.hrms.employee.dto.response.EmployeeSummary;
import com.forward.hrms.payroll.enums.PayrollRunStatus;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;

@Schema(name = "PayrollDashboard",
        description = "Composite for the Payroll persona. Drives the payroll-officer landing.")
public record PayrollDashboard(
        @Schema(nullable = true) CurrentRun currentRun,
        Runs6m runs6m,
        NeedsYou needsYou,
        @Schema(nullable = true) AnomalyScatter anomalyScatter,
        List<DepartmentBonus> bonusByDepartment90d,
        List<NetPoint> netYoYTrend12m
) {
    @Schema(name = "PayrollDashboard.CurrentRun",
            description = "Most recent run that hasn't been POSTED — DRAFT / LOCKED / COMPUTED / " +
                    "APPROVED. Falls back to the latest POSTED run when nothing is in flight. " +
                    "`status` carries the run's actual PayrollRunStatus, so a run caught mid-" +
                    "transition can also surface COMPUTING / POSTING / FAILED / CANCELLED.")
    public record CurrentRun(
            long id,
            @Schema(nullable = true) String fiscalYear,
            String period,
            @Schema(nullable = true) PayrollRunStatus status,
            @Schema(nullable = true) LocalDate cutoffDate,
            @Schema(nullable = true) Integer daysToCutoff,
            Rollup rollup
    ) {}

    @Schema(name = "PayrollDashboard.Rollup")
    public record Rollup(
            Money gross,
            Money deductions,
            Money net,
            int employeeCount
    ) {}

    @Schema(name = "PayrollDashboard.Runs6m",
            description = "Last 6 runs in the current fiscal year, oldest first for the chart.")
    public record Runs6m(List<RunRollup> runs) {}

    @Schema(name = "PayrollDashboard.RunRollup")
    public record RunRollup(
            long id,
            String period,
            Money gross,
            Money deductions,
            Money net
    ) {}

    @Schema(name = "PayrollDashboard.NeedsYou",
            description = "Action queues for the payroll officer.")
    public record NeedsYou(
            QueueSection anomaliesOnRun,
            QueueSection offCyclePending,
            QueueSection salaryChangesPending,
            QueueSection payslipsHeld
    ) {}

    @Schema(name = "PayrollDashboard.QueueSection")
    public record QueueSection(int count, List<QueueItem> items) {}

    @Schema(name = "PayrollDashboard.QueueItem")
    public record QueueItem(
            long id,
            @Schema(nullable = true) EmployeeSummary employee,
            String summary,
            @Schema(nullable = true) String reason,
            @Schema(nullable = true) LocalDateTime occurredAt
    ) {}

    @Schema(name = "PayrollDashboard.AnomalyScatter",
            description = "Prior-vs-current scatter. Empty until D-S3 (payroll-side scatter " +
                    "endpoint) lands.")
    public record AnomalyScatter(long runId, @Schema(nullable = true) Long comparedTo, List<ScatterPoint> points) {}

    @Schema(name = "PayrollDashboard.ScatterPoint")
    public record ScatterPoint(
            long employeeId,
            String name,
            @Schema(nullable = true) String departmentName,
            long priorNet,
            long currentNet,
            double variancePct
    ) {}

    @Schema(name = "PayrollDashboard.DepartmentBonus")
    public record DepartmentBonus(long departmentId, String name, Money total) {}

    @Schema(name = "PayrollDashboard.NetPoint")
    public record NetPoint(String month, long amount) {}
}
