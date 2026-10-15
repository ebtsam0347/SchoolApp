package com.forward.hrms.payroll.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

@Schema(name = "EmployeeAllowancesDto", description = "One employee's current (open) allowance set, for editing.")
public record EmployeeAllowancesDto(
        @Schema(description = "Employee display fields.") AllowanceEmployeeDto employee,
        @Schema(description = "Monthly rupees keyed by component code. Empty when the employee has none.")
        Map<String, BigDecimal> amounts,
        @Schema(description = "Each open allowance's own effectiveFrom, keyed by component code.")
        Map<String, LocalDate> since,
        @Schema(description = "Latest date any allowance changed (a line starting or closing); null when the employee never had allowances.", nullable = true)
        LocalDate lastChanged
) {}
