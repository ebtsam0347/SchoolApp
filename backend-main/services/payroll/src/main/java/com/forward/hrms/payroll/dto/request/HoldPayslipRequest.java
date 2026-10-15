package com.forward.hrms.payroll.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;

@Schema(name = "HoldPayslipRequest")
public record HoldPayslipRequest(
        @NotBlank String reason
) {}
