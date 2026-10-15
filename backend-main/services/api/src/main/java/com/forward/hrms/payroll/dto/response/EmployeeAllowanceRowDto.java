package com.forward.hrms.payroll.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

@Schema(name = "EmployeeAllowanceRowDto", description = "One employee's current (open) allowance set.")
public record EmployeeAllowanceRowDto(
        @Schema(description = "Employee id.") long employeeId,
        @Schema(description = "Employee display fields.") AllowanceEmployeeDto employee,
        @Schema(description = "Monthly rupees keyed by component code. A missing code means no allowance.")
        Map<String, BigDecimal> amounts,
        @Schema(description = "Each open allowance's own effectiveFrom, keyed by component code.")
        Map<String, LocalDate> since,
        @Schema(description = "Sum of `amounts`.") BigDecimal total,
        @Schema(description = "Latest date any allowance changed: a line starting (added or re-priced) or closing (re-priced or removed).")
        LocalDate lastChanged
) {}
