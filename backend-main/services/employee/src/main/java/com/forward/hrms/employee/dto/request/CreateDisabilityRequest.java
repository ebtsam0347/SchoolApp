package com.forward.hrms.employee.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "CreateDisabilityRequest",
        description = "HR-admin create payload for the disability catalog. `code` is unique and " +
                "immutable post-create — referenced by employee_disabilities.")
public record CreateDisabilityRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "PHYSICAL")
        @NotBlank @Size(min = 1, max = 32) String code,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Physical / orthopedic impairment")
        @NotBlank @Size(min = 1, max = 128) String name
) {}
