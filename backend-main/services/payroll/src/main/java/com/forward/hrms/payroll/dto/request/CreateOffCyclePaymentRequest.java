package com.forward.hrms.payroll.dto.request;

import com.forward.hrms.payroll.enums.OffCyclePaymentMode;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(name = "CreateOffCyclePaymentRequest")
public record CreateOffCyclePaymentRequest(
        @NotNull Long employeeId,
        @NotNull LocalDate paidOn,
        @NotNull @Positive BigDecimal amount,

        @Schema(description = "Id of the selected employee-service off-cycle payment type "
                + "(`GET /employees/off-cycle-payment-types?active=true`). Must exist and be "
                + "active. Replaces the old free-text `category`: the transaction's internal "
                + "source is always HR here and is never taken from the request.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Long offCyclePaymentTypeId,

        String reason,
        OffCyclePaymentMode paymentMode,
        String referenceNo
) {}
