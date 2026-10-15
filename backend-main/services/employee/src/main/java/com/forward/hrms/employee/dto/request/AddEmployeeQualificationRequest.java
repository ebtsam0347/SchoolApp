package com.forward.hrms.employee.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(name = "AddEmployeeQualificationRequest")
public record AddEmployeeQualificationRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long qualificationId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 255)
        @NotBlank @Size(max = 255) String institution,
        @Schema(nullable = true, maxLength = 255) @Size(max = 255) String boardOrUniversity,
        @Schema(nullable = true) Integer yearOfCompletion,
        @Schema(nullable = true, maxLength = 32) @Size(max = 32) String grade,
        @Schema(nullable = true, description = "MinIO key for an uploaded scan.")
        @Size(max = 512) String documentKey
) {}
