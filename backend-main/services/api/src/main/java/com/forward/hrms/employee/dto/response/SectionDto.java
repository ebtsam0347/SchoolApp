package com.forward.hrms.employee.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "SectionDto", description = "Section within a department.")
public record SectionDto(
        @Schema(description = "Internal id.", example = "12") long id,
        @Schema(description = "Catalog code.", example = "QC_1") String code,
        @Schema(description = "Display name.", example = "Quality Control - Footballs") String name,
        @Schema(description = "Parent department id.", example = "3") long departmentId,
        @Schema(description = "Parent department code.", example = "PROD") String departmentCode,
        @Schema(description = "Section head's employee id, if assigned.", nullable = true)
        Long headId,
        @Schema(description = "Soft-deactivation flag.") boolean active
) {}
