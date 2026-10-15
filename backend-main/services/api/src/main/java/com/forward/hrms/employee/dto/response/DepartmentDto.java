package com.forward.hrms.employee.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "DepartmentDto", description = "Top-level org unit. Departments are flat — no nesting.")
public record DepartmentDto(
        @Schema(description = "Internal id.", example = "3") long id,
        @Schema(description = "Catalog code.", example = "PROD") String code,
        @Schema(description = "Display name.", example = "Production") String name,
        @Schema(description = "Department head's employee id, if assigned.", nullable = true)
        Long headId,
        @Schema(description = "Soft-deactivation flag.") boolean active,
        @Schema(description = "Marks the department as a production unit (factory floor).") boolean isProduction
) {}
