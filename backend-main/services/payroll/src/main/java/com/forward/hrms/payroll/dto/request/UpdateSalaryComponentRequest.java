package com.forward.hrms.payroll.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(name = "UpdateSalaryComponentRequest", description = "Null fields are left unchanged.")
public record UpdateSalaryComponentRequest(
        @Size(max = 64) String name,
        @PositiveOrZero BigDecimal defaultAmount,
        Integer displayOrder,
        Boolean active,
        Boolean calculateWithSalary
) {}
