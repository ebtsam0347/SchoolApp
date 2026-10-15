package com.forward.hrms.payroll.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(name = "CreateIncrementBatchLineRequest",
        description = "Add one line to a DRAFT increment batch. Per-line `effectiveFrom` " +
                "overrides the batch default; omit to inherit it.")
public record CreateIncrementBatchLineRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "1234")
        @NotNull Long employeeId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "New basic in rupees.",
                example = "125000.00")
        @NotNull @PositiveOrZero BigDecimal newBasic,
        @Schema(description = "Optional new income-tax deduction (rupees). Replaces any current " +
                "INCOME_TAX line if set.", nullable = true)
        @PositiveOrZero BigDecimal newIncomeTax,
        @Schema(description = "Per-line override; falls back to the batch's effectiveFrom when null.",
                nullable = true)
        LocalDate effectiveFrom
) {}
