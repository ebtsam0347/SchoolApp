package com.forward.hrms.attendance.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

@Schema(name = "BulkOperatorPunchRequest",
        description = "End-of-shift / paper-form bulk insert of operator punches. Each row is a " +
                "single (employee, instant) punch with the operator's free-text reason. The whole " +
                "batch runs in one transaction — any single row failing rolls back the lot. Cap is " +
                "100 rows per call.")
public record BulkOperatorPunchRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "1..100 punch rows; the whole batch is one transaction.")
        @NotNull @NotEmpty @Size(max = 100) @Valid List<Row> punches
) {
    @Schema(name = "BulkOperatorPunchRequest.Row")
    public record Row(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long employeeId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalDateTime punchedAt,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String reason
    ) {}
}
