package com.forward.hrms.employee.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "SkillDto", description = "Skill catalog. Per-employee proficiency lives in employee_skills.")
public record SkillDto(
        @Schema(description = "Internal id.", example = "11") long id,
        @Schema(description = "Catalog code.", example = "TECH_JAVA") String code,
        @Schema(description = "Display name.", example = "Java") String name,
        @Schema(description = "Loose category — TECH | LANGUAGE | TRADE | SPORT | OTHER.", example = "TECH",
                nullable = true) String category,
        @Schema(description = "Soft-deactivation flag.") boolean active
) {}
