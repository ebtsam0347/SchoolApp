package com.forward.hrms.employee.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

@Schema(name = "EmployeeStats",
        description = "Headline KPIs for the People-overview screen — counts by status / type / probation " +
                "windows / cert windows / ESS coverage.")
public record EmployeeStats(
        @Schema(description = "Total non-terminated employees.") long total,
        @Schema(description = "Counts keyed by EmploymentStatus.") Map<String, Long> byStatus,
        @Schema(description = "Counts keyed by EmploymentType (excluding TERMINATED rows).")
        Map<String, Long> byEmploymentType,
        Probation probation,
        Certifications certifications,
        Ess ess
) {

    @Schema(name = "EmployeeStats.Probation")
    public record Probation(long active, long dueWithin14, long overdue) {}

    @Schema(name = "EmployeeStats.Certifications")
    public record Certifications(long expiringWithin30, long expired) {}

    @Schema(name = "EmployeeStats.Ess")
    public record Ess(long granted, long ungranted, double coveragePct) {}
}
