package com.forward.hrms.payroll.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(name = "UpdateSalaryLineRequest",
        description = "Closes the current row and inserts a new one with the new amount.")
public record UpdateSalaryLineRequest(
        @NotNull @PositiveOrZero BigDecimal amount,
        @NotNull LocalDate effectiveFrom,
        String notes
) {}
