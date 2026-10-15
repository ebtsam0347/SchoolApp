package com.forward.hrms.employee.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Size;

@Schema(name = "UpdateProvinceRequest", description = "Patch a province. Null fields are left unchanged.")
public record UpdateProvinceRequest(
        @Schema(description = "Display name.", example = "Punjab", maxLength = 64)
        @Size(max = 64) String name,

        @Schema(description = "Soft-deactivation flag.")
        Boolean active
) {}
