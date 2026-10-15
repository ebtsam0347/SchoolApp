package com.forward.hrms.payroll.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(name = "ReviseBasicSalaryRequest")
public record ReviseBasicSalaryRequest(
        @Schema(description = "New monthly basic in rupees.",
                requiredMode = Schema.RequiredMode.REQUIRED, example = "50000.00")
        @NotNull @PositiveOrZero BigDecimal basic,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull LocalDate effectiveFrom,

        String revisionReason
) {}
