package com.forward.hrms.payroll.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(name = "CreatePermanentAdvanceRequest",
        description = "Long-term advance against future salary. Created in PENDING; the employee's " +
                "manager approves first, then HR, then Accounts. All money fields are rupees. The " +
                "employee's gratuity as of today is computed server-side and stored as gratuityAtRequest.")
public record CreatePermanentAdvanceRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long employeeId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Principal amount, rupees.")
        @NotNull @Positive BigDecimal amount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Regular monthly installment, rupees.")
        @NotNull @Positive BigDecimal monthlyDeduction,
        @Schema(nullable = true, description = "Rupees taken against bonus when paid.") @PositiveOrZero BigDecimal bonusOffset,
        @Schema(nullable = true, description = "Rupees taken against AL encashment when paid.") @PositiveOrZero BigDecimal annualLeaveOffset,
        @Schema(nullable = true) LocalDate receivingDate,
        @Schema(nullable = true) String description,
        @Schema(nullable = false, description = "Current financial year, e.g. 2023-24") @NotNull String financialYearCode,
        @Schema(nullable = true, description = "Rupees taken against bonus when paid.") @PositiveOrZero BigDecimal bonusOffset2,
        @Schema(nullable = true, description = "Month for annual leave.") LocalDate annualLeaveMonth
) {}
