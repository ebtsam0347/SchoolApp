package com.forward.hrms.attendance.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

@Schema(name = "AssignShiftRequest")
public record AssignShiftRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long shiftId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalDate effectiveFrom,
        @Schema(nullable = true) LocalDate effectiveTo
) {}
