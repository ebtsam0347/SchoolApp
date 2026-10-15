package com.forward.hrms.payroll.dto.request;

import com.forward.hrms.payroll.enums.OffCyclePaymentMode;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(name = "UpdateOffCyclePaymentRequest", description = "Null fields are left unchanged.")
public record UpdateOffCyclePaymentRequest(
        @Positive BigDecimal amount,

        @Schema(nullable = true,
                description = "Re-point at a different employee-service off-cycle payment type. "
                        + "Validated the same way as on create — must exist and be active. The "
                        + "transaction's source is never changed by an edit.")
        Long offCyclePaymentTypeId,

        String reason,
        OffCyclePaymentMode paymentMode,
        String referenceNo,
        LocalDate paidOn
) {}
