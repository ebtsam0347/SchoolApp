package com.forward.hrms.employee.dto.response;

import com.forward.hrms.employee.enums.DesignationCategory;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "DesignationDto", description = "Job title catalog. Department-agnostic.")
public record DesignationDto(
        @Schema(description = "Internal id.", example = "7") long id,
        @Schema(description = "Catalog code.", example = "SR_ENG") String code,
        @Schema(description = "Display title.", example = "Senior Engineer") String title,
        @Schema(description = "DIRECT (production-line) vs INDIRECT (office / admin). " +
                "Null when the distinction does not apply.", nullable = true) DesignationCategory category,
        @Schema(description = "Soft-deactivation flag.") boolean active
) {}
