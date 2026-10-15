package com.forward.hrms.payroll.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record SettlementPaymentRequest(

        @NotNull
        LocalDate paymentDate,

        @NotBlank
        String paymentMethod,

        String referenceNo,

        String remarks

) {}
