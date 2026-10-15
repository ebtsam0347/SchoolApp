package com.forward.hrms.employee.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

@Schema(name = "LiftSuspensionRequest")
public record LiftSuspensionRequest(
        @Schema(description = "Actual lift date. Defaults to today when omitted.", nullable = true)
        LocalDate liftedOn,

        @Schema(description = "Why the suspension is being lifted.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank String reason
) {}
