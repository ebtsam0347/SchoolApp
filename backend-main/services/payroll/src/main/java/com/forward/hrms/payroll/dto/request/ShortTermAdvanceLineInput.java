package com.forward.hrms.payroll.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

@Schema(name = "ShortTermAdvanceLineInput", description = "One employee + amount inside a batch. Amount is rupees.")
public record ShortTermAdvanceLineInput(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long employeeId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @Positive BigDecimal amount
) {}
