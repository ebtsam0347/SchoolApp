package com.forward.hrms.payroll.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(name = "RecordPermanentAdvanceDeductionRequest",
        description = "Manual / off-cycle ledger entry for a permanent advance. The payroll run " +
                "writes its own monthly installment rows; this endpoint covers out-of-band " +
                "deductions HR / Accounts record by hand. Always recorded as deducted_amount.")
public record RecordPermanentAdvanceDeductionRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Rupees. Must not exceed the employee's remaining permanent-advance balance.")
        @NotNull @Positive BigDecimal deductedAmount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalDate transactionDate,
        @Schema(nullable = true,
                description = "Defaults to \"Manual deduction for {employeeCode}\" when blank.")
        String description
) {}
