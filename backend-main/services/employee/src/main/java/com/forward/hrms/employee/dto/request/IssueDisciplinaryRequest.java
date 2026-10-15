package com.forward.hrms.employee.dto.request;

import com.forward.hrms.employee.enums.DisciplinaryCategory;
import com.forward.hrms.employee.enums.DisciplinaryLevel;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

@Schema(name = "IssueDisciplinaryRequest")
public record IssueDisciplinaryRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull DisciplinaryLevel level,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull DisciplinaryCategory category,
        @Schema(nullable = true) LocalDate incidentDate,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalDate issuedOn,
        @Schema(description = "Short narrative — what the employee did.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank String reason
) {}
