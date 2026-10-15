package com.forward.hrms.employee.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(name = "ExtendProbationRequest",
        description = "One-time extension of an active PROBATION contract. " +
                "newProbationDays must be 120 or 180, and greater than the current probation_days.")
public record ExtendProbationRequest(
        @Schema(description = "New total probation length in days. Allowed: 120 or 180.",
                example = "120", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Integer newProbationDays,

        @Schema(description = "Reason for the extension.", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank String reason
) {}
