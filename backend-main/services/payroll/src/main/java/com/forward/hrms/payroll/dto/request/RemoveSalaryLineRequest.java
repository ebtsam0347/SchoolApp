package com.forward.hrms.payroll.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

@Schema(name = "RemoveSalaryLineRequest", description = "Sets effective_to on the current row.")
public record RemoveSalaryLineRequest(
        @NotNull LocalDate effectiveTo
) {}
