package com.forward.hrms.employee.dto.request;

import com.forward.hrms.employee.enums.DesignationCategory;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "CreateDesignationRequest")
public record CreateDesignationRequest(
        @Schema(description = "Catalog code.", example = "SR_ENG",
                requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 32)
        @NotBlank @Size(max = 32) String code,

        @Schema(description = "Display title.", example = "Senior Engineer",
                requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 128)
        @NotBlank @Size(max = 128) String title,

        @Schema(description = "DIRECT (production-line / labour) vs INDIRECT (office / admin). " +
                "Optional — null when the distinction does not apply.", nullable = true)
        DesignationCategory category
) {}
