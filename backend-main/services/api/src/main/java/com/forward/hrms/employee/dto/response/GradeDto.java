package com.forward.hrms.employee.dto.response;

import com.forward.hrms.employee.enums.GradeType;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "GradeDto", description = "Seniority grade catalog. Higher level = more senior.")
public record GradeDto(
        @Schema(description = "Internal id.", example = "4") long id,
        @Schema(description = "Catalog code.", example = "G3") String code,
        @Schema(description = "Display name.", example = "Grade 3") String name,
        @Schema(description = "Ordering hint; higher is more senior.", example = "3") int level,
        @Schema(description = "Optional description.", nullable = true) String description,
        @Schema(description = "Soft-deactivation flag.") boolean active,
        @Schema(description = "HR-controlled sort order; lower sorts first.", example = "30") int displayOrder,
        @Schema(description = "Role bucket. Drives payroll's canteen rule (management → weekly, " +
                "supervisor/labour → per-day) and other grade-type policy switches.")
        GradeType gradeType
) {}
