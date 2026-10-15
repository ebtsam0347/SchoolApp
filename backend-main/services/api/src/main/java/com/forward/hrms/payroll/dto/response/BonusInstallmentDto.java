package com.forward.hrms.payroll.dto.response;

import com.forward.hrms.payroll.enums.BonusInstallmentStatus;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;

@Schema(name = "BonusInstallmentDto")
public record BonusInstallmentDto(
        long id,
        long bonusEventId,
        int installmentNo,
        BigDecimal percentage,
        LocalDate paidOn,
        BonusInstallmentStatus status,
        @Schema(nullable = true) LocalDateTime appliedAt,
        @Schema(nullable = true) Long appliedBy
) {}
