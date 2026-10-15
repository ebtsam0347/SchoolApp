package com.forward.hrms.attendance.dto.request;

import com.forward.hrms.attendance.enums.PunchDirection;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

@Schema(name = "OperatorPunchRequest",
        description = "Operator-added punch with offline proof. `reason` is the paper-trail note " +
                "(\"missed checkout, paper form #12 / approved by supervisor X\").")
public record OperatorPunchRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long employeeId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalDateTime punchedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String reason,
        @Schema(nullable = true, description = "IN makes this punch the day's check-in, OUT its " +
                "check-out, even when other punches are earlier / later (the latest such punch " +
                "wins). Omit to derive by position: earliest is check-in, latest check-out.")
        PunchDirection direction
) {}
