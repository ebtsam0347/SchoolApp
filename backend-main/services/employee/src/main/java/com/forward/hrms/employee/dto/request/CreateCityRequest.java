package com.forward.hrms.employee.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(name = "CreateCityRequest")
public record CreateCityRequest(
        @Schema(description = "Catalog code (uppercase).", example = "SKT",
                requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 32)
        @NotBlank @Size(max = 32) String code,

        @Schema(description = "Display name.", example = "Sialkot",
                requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 128)
        @NotBlank @Size(max = 128) String name,

        @Schema(description = "Province id.", example = "1",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Long provinceId
) {}
