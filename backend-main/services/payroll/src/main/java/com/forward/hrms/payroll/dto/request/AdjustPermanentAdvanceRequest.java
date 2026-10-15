package com.forward.hrms.payroll.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(name = "AdjustPermanentAdvanceRequest",
        description = "HR's revision of a permanent advance at the HR stage, before approving it. " +
                "Money fields are rupees.")
public record AdjustPermanentAdvanceRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Revised principal amount, rupees.")
        @NotNull @Positive BigDecimal amount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "Revised monthly installment, rupees. Cannot exceed `amount`.")
        @NotNull @Positive BigDecimal monthlyDeduction,
        @Schema(nullable = true, description = "Why the figures were changed.") @Size(max = 1024) String note
) {}
