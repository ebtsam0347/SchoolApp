package com.forward.hrms.payroll.dto.request;

import com.forward.hrms.payroll.enums.FiscalYearStatus;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;

@Schema(name = "UpdateFiscalYearRequest")
public record UpdateFiscalYearRequest(
        @NotNull FiscalYearStatus status
) {}
