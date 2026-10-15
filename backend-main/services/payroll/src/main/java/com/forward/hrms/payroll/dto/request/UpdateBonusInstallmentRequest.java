package com.forward.hrms.payroll.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(name = "UpdateBonusInstallmentRequest", description = "DRAFT-only.")
public record UpdateBonusInstallmentRequest(
        @DecimalMin("0.01") @DecimalMax("100.00") BigDecimal percentage,
        LocalDate paidOn
) {}
