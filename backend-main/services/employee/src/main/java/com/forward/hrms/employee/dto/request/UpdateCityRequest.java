package com.forward.hrms.employee.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Size;

@Schema(name = "UpdateCityRequest", description = "Patch a city. Null fields are left unchanged.")
public record UpdateCityRequest(
        @Schema(description = "Display name.", example = "Sialkot", maxLength = 128)
        @Size(max = 128) String name,

        @Schema(description = "Move to a different province.", example = "1")
        Long provinceId,

        @Schema(description = "Soft-deactivation flag.")
        Boolean active
) {}
