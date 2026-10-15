package com.forward.hrms.employee.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "CreateQualificationRequest")
public record CreateQualificationRequest(
        @Schema(description = "Catalog code.", example = "BSC_CS",
                requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 64)
        @NotBlank @Size(max = 64) String code,

        @Schema(description = "Display name.", example = "BSc Computer Science",
                requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 255)
        @NotBlank @Size(max = 255) String name,

        @Schema(description = "MATRIC | INTERMEDIATE | DIPLOMA | BACHELORS | MASTERS | MPHIL | PHD | OTHER.",
                example = "BACHELORS", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 32)
        @NotBlank @Size(max = 32) String level
) {}
