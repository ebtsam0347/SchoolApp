package com.forward.hrms.payroll.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(name = "UpdateIncrementBatchLineRequest", description = "DRAFT-only.")
public record UpdateIncrementBatchLineRequest(
        @PositiveOrZero BigDecimal newBasic,
        @PositiveOrZero BigDecimal newIncomeTax,
        LocalDate effectiveFrom
) {}
