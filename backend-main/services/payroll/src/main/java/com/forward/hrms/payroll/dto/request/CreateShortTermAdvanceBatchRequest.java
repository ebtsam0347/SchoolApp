package com.forward.hrms.payroll.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(name = "CreateShortTermAdvanceBatchRequest",
        description = "Bulk short-term advance request. All lines are deducted in full from the target month's payroll.")
public record CreateShortTermAdvanceBatchRequest(
        @Schema(nullable = true) Long departmentId,
        @Schema(nullable = true) Long sectionId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Integer targetYear,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "1..12 — the payroll month to deduct in.")
        @NotNull @Min(1) @Max(12) Integer targetMonth,
        @Schema(nullable = true) @Size(max = 1024) String notes,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "One row per employee. Each employeeId must be unique within the batch.")
        @NotEmpty @Valid List<ShortTermAdvanceLineInput> lines
) {}
