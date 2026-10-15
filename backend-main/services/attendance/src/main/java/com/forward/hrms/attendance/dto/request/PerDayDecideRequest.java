package com.forward.hrms.attendance.dto.request;

import com.forward.hrms.attendance.enums.ApproveAction;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

@Schema(name = "PerDayDecideRequest",
        description = "Per-day approve/reject — only allowed when the current PENDING stage is the FINAL stage.")
public record PerDayDecideRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotEmpty @Valid List<Decision> decisions
) {

    @Schema(name = "PerDayDecision")
    public record Decision(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalDate workDate,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull ApproveAction action,
            @Schema(nullable = true) String reason
    ) {}
}
