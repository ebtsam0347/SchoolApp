package com.forward.hrms.payroll.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.Map;

@Schema(name = "SetAllowancesRequest",
        description = "Replace an employee's allowance set as of today. A changed amount closes the open line " +
                "today and opens a new one today; 0 or an absent code closes that allowance today; unchanged " +
                "amounts are left alone.")
public record SetAllowancesRequest(
        @Schema(description = "Monthly rupees keyed by allowance component code.",
                example = "{\"TRAVEL\": 5500, \"MOBILE\": 1500}", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Map<String, @NotNull @PositiveOrZero BigDecimal> amounts
) {}
