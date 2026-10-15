package com.forward.hrms.employee.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ProvinceDto", description = "Province / federal area in Pakistan.")
public record ProvinceDto(
        @Schema(description = "Internal id.", example = "1") long id,
        @Schema(description = "ISO 3166-2:PK style code.", example = "PB") String code,
        @Schema(description = "Display name.", example = "Punjab") String name,
        @Schema(description = "Soft-deactivation flag.") boolean active
) {}
