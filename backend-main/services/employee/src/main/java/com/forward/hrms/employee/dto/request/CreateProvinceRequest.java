package com.forward.hrms.employee.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "CreateProvinceRequest")
public record CreateProvinceRequest(
        @Schema(description = "ISO 3166-2:PK style code (uppercase).", example = "PB",
                requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 16)
        @NotBlank @Size(max = 16) String code,

        @Schema(description = "Display name.", example = "Punjab",
                requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 64)
        @NotBlank @Size(max = 64) String name
) {}
