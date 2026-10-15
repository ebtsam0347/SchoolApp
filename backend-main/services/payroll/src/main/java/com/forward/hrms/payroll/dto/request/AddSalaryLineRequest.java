package com.forward.hrms.payroll.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(name = "AddSalaryLineRequest")
public record AddSalaryLineRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long componentId,

        @Schema(description = "Rupees. Optional — defaults to component.defaultAmount when omitted.",
                example = "15000.00", nullable = true)
        @PositiveOrZero BigDecimal amount,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalDate effectiveFrom,

        String notes
) {}
