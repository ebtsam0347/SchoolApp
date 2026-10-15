package com.forward.hrms.payroll.dto.request;

import com.forward.hrms.payroll.enums.CalcType;
import com.forward.hrms.payroll.enums.ComponentType;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(name = "CreateSalaryComponentRequest")
public record CreateSalaryComponentRequest(
        @Schema(example = "HRA", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 32)
        @NotBlank @Size(max = 32) String code,

        @Schema(example = "House Rent Allowance",
                requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 64)
        @NotBlank @Size(max = 64) String name,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull ComponentType type,

        @Schema(description = "Defaults to FLAT. PER_DAY restricted to EARNING.")
        CalcType calcType,

        @Schema(description = "Hint HR can override per employee. Rupees.",
                example = "15000.00", nullable = true)
        @PositiveOrZero BigDecimal defaultAmount,

        @Schema(example = "10", nullable = true) Integer displayOrder,

        @Schema(example = "false", nullable = false) Boolean calculateWithSalary
) {}
