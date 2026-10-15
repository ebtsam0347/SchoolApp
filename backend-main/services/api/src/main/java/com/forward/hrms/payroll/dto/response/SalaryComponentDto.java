package com.forward.hrms.payroll.dto.response;

import com.forward.hrms.payroll.enums.CalcType;
import com.forward.hrms.payroll.enums.ComponentType;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(name = "SalaryComponentDto",
        description = "Catalog row. All amounts are flat (no percentage, no formula).")
public record SalaryComponentDto(
        @Schema(example = "5") long id,
        @Schema(example = "HRA") String code,
        @Schema(example = "House Rent Allowance") String name,
        ComponentType type,
        CalcType calcType,
        @Schema(description = "Optional default amount (rupees) HR can override per employee.",
                example = "15000.00", nullable = true) BigDecimal defaultAmount,
        @Schema(example = "10") int displayOrder,
        boolean active,
        boolean calculateWithSalary
) {}
