package com.forward.hrms.employee.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "QualificationDto",
        description = "Academic qualification catalog. Per-employee instances live in employee_qualifications.")
public record QualificationDto(
        @Schema(description = "Internal id.", example = "8") long id,
        @Schema(description = "Catalog code.", example = "BSC_CS") String code,
        @Schema(description = "Display name.", example = "BSc Computer Science") String name,
        @Schema(description = "MATRIC | INTERMEDIATE | DIPLOMA | BACHELORS | MASTERS | MPHIL | PHD | OTHER.",
                example = "BACHELORS") String level,
        @Schema(description = "Soft-deactivation flag.") boolean active,
        @Schema(description = "HR-controlled sort order; lower sorts first.", example = "40") int displayOrder
) {}
