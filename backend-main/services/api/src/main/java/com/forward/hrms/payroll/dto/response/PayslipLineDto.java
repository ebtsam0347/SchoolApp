package com.forward.hrms.payroll.dto.response;

import com.forward.hrms.payroll.enums.CalcType;
import com.forward.hrms.payroll.enums.ComponentType;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(name = "PayslipLineDto")
public record PayslipLineDto(
        long id,
        @Schema(nullable = true) Long componentId,
        String code,
        String name,
        ComponentType type,
        CalcType calcType,
        @Schema(nullable = true, description = "Per-day rate in rupees (PER_DAY only).") BigDecimal rate,
        @Schema(nullable = true, description = "Days multiplier (PER_DAY only); fractional for half-days.") BigDecimal days,
        @Schema(description = "Final line amount in rupees.") BigDecimal amount,
        int displayOrder
) {}
