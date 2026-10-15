package com.forward.hrms.payroll.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(name = "CreatePayrollRunRequest")
public record CreatePayrollRunRequest(
        @NotNull @Min(2020) Integer year,
        @NotNull @Min(1) @Max(12) Integer month
) {}
