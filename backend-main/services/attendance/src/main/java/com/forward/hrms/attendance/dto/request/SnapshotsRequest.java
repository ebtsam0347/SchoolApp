package com.forward.hrms.attendance.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * Batch input for {@link com.forward.hrms.attendance.controller.AttendanceController#snapshots}.
 * Cap 500 — matches payroll's largest run size today; tighten when the
 * SQL aggregate proves expensive.
 */
@Schema(description = "Batch payroll snapshot request.")
public record SnapshotsRequest(
        @Schema(description = "Employees to snapshot (1..500).")
        @NotEmpty @Size(max = 500) List<Long> employeeIds,
        @Schema(description = "Period start (inclusive).") @NotNull LocalDate from,
        @Schema(description = "Period end (inclusive).") @NotNull LocalDate to
) {}
