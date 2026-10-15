package com.forward.hrms.payroll.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(name = "CreateLeaveEncashmentRequest",
        description = "Cash out unused annual leaves. Amount = days × (basicSalary / 26). " +
                "One row per (employee, calculationMonth).")
public record CreateLeaveEncashmentRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long employeeId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "1st of the period start month.")
        @NotNull LocalDate calculationMonth,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "12",
                description = "Period length in months — usually 12 (annual cycle).")
        @NotNull @Positive Integer periodInMonths,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull @DecimalMin("0.01") BigDecimal daysEncashed,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Snapshot basic salary used for the calc, rupees.")
        @NotNull @Positive BigDecimal basicSalary,
        @Schema(nullable = true) String description
) {}
