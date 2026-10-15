package com.forward.hrms.employee.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "CityDto", description = "City in Pakistan, scoped to a province.")
public record CityDto(
        @Schema(description = "Internal id.", example = "5") long id,
        @Schema(description = "Catalog code.", example = "SKT") String code,
        @Schema(description = "Display name.", example = "Sialkot") String name,
        @Schema(description = "Province id.", example = "1") long provinceId,
        @Schema(description = "Province code (denormalized for filters).", example = "PB") String provinceCode,
        @Schema(description = "Soft-deactivation flag.") boolean active,
        @Schema(description = "HR-controlled sort order; lower sorts first.", example = "10") int displayOrder
) {}
