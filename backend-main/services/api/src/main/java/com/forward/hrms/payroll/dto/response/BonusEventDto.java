package com.forward.hrms.payroll.dto.response;

import com.forward.hrms.payroll.enums.BonusEventStatus;
import com.forward.hrms.payroll.enums.BonusEventType;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;

@Schema(name = "BonusEventDto")
public record BonusEventDto(
        long id,
        String fiscalYearCode,
        String name,
        BonusEventType eventType,
        LocalDate referenceDate,
        int fullServiceDaysThreshold,
        BonusEventStatus status,
        @Schema(nullable = true) String notes,
        @Schema(nullable = true) LocalDateTime activatedAt,
        @Schema(nullable = true) Long activatedBy,
        @Schema(nullable = true) List<BonusInstallmentDto> installments,
        @Schema(nullable = true) List<BonusEventAmountDto> amounts
) {}
