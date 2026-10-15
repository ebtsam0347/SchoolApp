package com.forward.hrms.attendance.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * Per-employee payroll snapshot. Mirrors the aggregation payroll's
 * {@code AttendanceClient.snapshot} computes today, just precomputed
 * server-side so payroll doesn't loop over per-day rows.
 */
@Schema(description = "Per-employee aggregated attendance snapshot for a payroll period.")
public record AttendanceSnapshotDto(
        @Schema(description = "Days marked ABSENT in the period.") BigDecimal lopDays,
        @Schema(description = "PRESENT + 0.5 × HALF_DAY (rounded up).") int daysPresent,
        @Schema(description = "Sum of OT minutes / 60.") BigDecimal otHours,
        @Schema(description = "daysInPeriod − holidayCount − sundayCount.") int workingDays,
        @Schema(description = "workingDays − absentDays (≥ 0).") BigDecimal paidDays,
        @Schema(description = "absentCount + (halfDayCount / 2).") BigDecimal absentDays,
        @Schema(description = "HOLIDAY count.") int holidaysInPeriod,
        @Schema(description = "WEEKEND count.") int sundaysInPeriod
) {}
