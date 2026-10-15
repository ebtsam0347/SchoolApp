package com.forward.hrms.employee.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@Schema(name = "AddPriorExperienceRequest")
public record AddPriorExperienceRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 255)
        @NotBlank @Size(max = 255) String employerName,
        @Size(max = 255) String designation,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalDate startDate,
        @Schema(description = "Required — every prior employer has been left.",
                requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalDate endDate,
        String responsibilities,
        @Size(max = 255) String reasonForLeaving
) {}
