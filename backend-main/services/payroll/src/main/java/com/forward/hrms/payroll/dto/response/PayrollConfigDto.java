package com.forward.hrms.payroll.dto.response;

import com.forward.hrms.payroll.entity.PayrollConfig;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(name = "PayrollConfigDto")
public record PayrollConfigDto(
        long id,
        LocalDate effectiveFrom,
        @Schema(nullable = true) LocalDate effectiveTo,
        @Schema(description = "Monthly EOBI deduction in rupees (employee share).") BigDecimal eobiAmount,
        BigDecimal pessiRatePercent,
        @Schema(description = "PESSI cap on gross in rupees.") BigDecimal pessiSalaryCap,
        int gratuityMinServiceMonths,
        int gratuityPartialYearThresholdMonths,
        @Schema(description = "Canteen meal price (rupees per meal) for MANAGEMENT-grade employees. " +
                "Compute charges weeks-with-canteen × (rate × 7).")
        BigDecimal canteenPerDayRateManagement,
        @Schema(description = "Canteen meal price (rupees per meal) for SUPERVISOR / LABOUR grades. " +
                "Compute charges days × rate.")
        BigDecimal canteenPerDayRateOther,
        @Schema(nullable = true) String notes
) {
    public static PayrollConfigDto of(PayrollConfig s) {
        return new PayrollConfigDto(s.getId(), s.getEffectiveFrom(), s.getEffectiveTo(),
                s.getEobiAmount(), s.getPessiRatePercent(), s.getPessiSalaryCap(),
                s.getGratuityMinServiceMonths(), s.getGratuityPartialYearThresholdMonths(),
                s.getCanteenPerDayRateManagement(), s.getCanteenPerDayRateOther(),
                s.getNotes());
    }
}
