package com.forward.hrms.employee.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

@Schema(name = "SuspendRequest")
public record SuspendRequest(
        @Schema(description = "Day the suspension begins.", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull LocalDate startDate,

        @Schema(description = "Planned end. Omit for open-ended.", nullable = true)
        LocalDate endDate,

        @Schema(description = "Why the employee is being suspended.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank String reason,

        @Schema(description = "When false, payroll counts these days as LOP.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Boolean withPay
) {}
