package com.forward.hrms.payroll.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(name = "BonusEventAmountDto",
        description = "Frozen per-employee snapshot at activation.")
public record BonusEventAmountDto(
        long id,
        long bonusEventId,
        long employeeId,
        LocalDate hireDate,
        int serviceDays,
        BigDecimal basicSnapshot,
        @Schema(description = "100% bonus amount in rupees.") BigDecimal fullBonus
) {}
