package com.forward.hrms.payroll.dto.request;

import com.forward.hrms.payroll.enums.OffCyclePaymentMode;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ApplyBonusInstallmentRequest")
public record ApplyBonusInstallmentRequest(
        @Schema(description = "Defaults to BANK when omitted.")
        OffCyclePaymentMode paymentMode
) {}
