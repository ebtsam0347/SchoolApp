package com.forward.hrms.payroll.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(name = "CreateBonusInstallmentRequest")
public record CreateBonusInstallmentRequest(
        @NotNull @Positive Integer installmentNo,
        @NotNull @DecimalMin("0.01") @DecimalMax("100.00") BigDecimal percentage,
        @NotNull LocalDate paidOn
) {}
