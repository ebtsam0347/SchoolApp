package com.forward.hrms.payroll.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(name = "GratuityDto",
        description = "Read-only gratuity calculator. Computed on demand from gratuity_start_date " +
                "(falls back to hire_date) + current effective basic + payroll_config.")
public record GratuityDto(
        long employeeId,
        @Schema(description = "Gratuity start date — falls back to hireDate when no override is set on " +
                "employee_payroll_settings.")
        LocalDate gratuityStartDate,
        LocalDate hireDate,
        LocalDate asOf,
        int totalServiceMonths,
        boolean eligible,
        @Schema(description = "Whole years of service actually completed (totalServiceMonths / 12).")
        int completedYears,
        @Schema(description = "Number of gratuities payable — completedYears, plus one when the leftover " +
                "months reach the partial-year threshold. 7 months' service → 1 with completedYears 0. " +
                "0 when not eligible. The gratuity amount is basic / 26 × 30 × this.")
        int noOfGratuities,
        BigDecimal lastBasic,
        BigDecimal gratuity,
        long configId
) {}
