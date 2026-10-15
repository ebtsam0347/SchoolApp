package com.forward.hrms.payroll.dto.request;

import com.forward.hrms.payroll.enums.OffCyclePaymentMode;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Accounts' record of how the payment was actually executed. Reuses the
 * row's existing {@code paymentMode} / {@code referenceNo} fields rather
 * than introducing a parallel requested-vs-actual pair.
 */
@Schema(name = "PayOffCyclePaymentRequest",
        description = "Actual payment details recorded by Accounts when releasing the payment.")
public record PayOffCyclePaymentRequest(

        @Schema(description = "Mode the payment was actually made in. Overwrites the mode HR "
                + "entered at creation.", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull OffCyclePaymentMode paymentMode,

        @Schema(nullable = true,
                description = "Bank transaction / cheque / voucher number. Optional — a cash "
                        + "handover has none. Overwrites the reference HR entered, when supplied.")
        String referenceNo,

        @Schema(nullable = true,
                description = "Actual date the money left. Omit to keep the date HR recorded.")
        LocalDate paidOn
) {}
