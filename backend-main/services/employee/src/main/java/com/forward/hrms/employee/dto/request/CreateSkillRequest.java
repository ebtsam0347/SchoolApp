package com.forward.hrms.employee.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "CreateSkillRequest")
public record CreateSkillRequest(
        @Schema(description = "Catalog code.", example = "TECH_JAVA",
                requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 32)
        @NotBlank @Size(max = 32) String code,

        @Schema(description = "Display name.", example = "Java",
                requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 128)
        @NotBlank @Size(max = 128) String name,

        @Schema(description = "TECH | LANGUAGE | TRADE | SPORT | OTHER.", example = "TECH", maxLength = 32)
        @Size(max = 32) String category
) {}
