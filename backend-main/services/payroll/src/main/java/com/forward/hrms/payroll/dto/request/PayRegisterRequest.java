package com.forward.hrms.payroll.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;

@Schema(description = "Pay register report request — one specific payroll run.")
public record PayRegisterRequest(
        @Schema(description = "Payroll run id whose payslips form the register.",
                requiredMode = Schema.RequiredMode.REQUIRED,
                example = "42")
        @NotNull
        Long payrollRunId
) {}
