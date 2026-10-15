package com.forward.hrms.payroll.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

@Schema(name = "CreateFiscalYearRequest")
public record CreateFiscalYearRequest(
        @Schema(example = "2026-27", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Pattern(regexp = "\\d{4}-\\d{2}") String code,

        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate
) {}
