package com.forward.hrms.payroll.dto.response;

import com.forward.hrms.payroll.enums.CalcType;
import com.forward.hrms.payroll.enums.ComponentType;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(name = "EmployeeSalaryComponentDto")
public record EmployeeSalaryComponentDto(
        long id,
        long employeeId,
        long componentId,
        @Schema(example = "HRA") String componentCode,
        String componentName,
        boolean calculateWithSalary,
        ComponentType type,
        CalcType calcType,
        @Schema(description = "Rupees. Monthly for FLAT, per-day for PER_DAY.") BigDecimal amount,
        LocalDate effectiveFrom,
        @Schema(nullable = true) LocalDate effectiveTo,
        @Schema(nullable = true) String notes
) {}
