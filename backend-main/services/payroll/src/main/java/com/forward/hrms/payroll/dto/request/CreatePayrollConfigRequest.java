package com.forward.hrms.payroll.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(name = "CreatePayrollConfigRequest",
        description = "Inserts a new effective-dated row and closes the previous one.")
public record CreatePayrollConfigRequest(
        @NotNull LocalDate effectiveFrom,

        @Schema(description = "Monthly EOBI deduction in rupees.")
        @NotNull @PositiveOrZero BigDecimal eobiAmount,

        @Schema(example = "6.00")
        @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal pessiRatePercent,

        @Schema(description = "PESSI cap in rupees.")
        @NotNull @PositiveOrZero BigDecimal pessiSalaryCap,

        Integer gratuityMinServiceMonths,
        Integer gratuityPartialYearThresholdMonths,

        @Schema(description = "Canteen meal price (rupees per meal) for MANAGEMENT-grade employees. " +
                "Defaults to 0 when omitted.", nullable = true)
        @PositiveOrZero BigDecimal canteenPerDayRateManagement,

        @Schema(description = "Canteen meal price (rupees per meal) for SUPERVISOR / LABOUR grades. " +
                "Defaults to 0 when omitted.", nullable = true)
        @PositiveOrZero BigDecimal canteenPerDayRateOther,

        String notes
) {}
