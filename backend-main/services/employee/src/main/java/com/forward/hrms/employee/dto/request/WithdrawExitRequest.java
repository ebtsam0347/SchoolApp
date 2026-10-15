package com.forward.hrms.employee.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;

@Schema(name = "WithdrawExitRequest")
public record WithdrawExitRequest(
        @Schema(description = "Why the resignation is being withdrawn.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank String reason
) {}
