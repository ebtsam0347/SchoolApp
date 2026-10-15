package com.forward.hrms.employee.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Catalog labels resolved server-side for one employee row — saves the
 * UI from joining department/section/designation/grade/city by id on
 * every render. Pure transport DTO; the resolver lives in each
 * service that produces it (employee-service for the directory and
 * /me composites, BFF for cross-service composites that need the
 * shared cache).
 */
@Schema(name = "EmployeeResolvedLabels",
        description = "Resolved id→label rows for the catalogs the page renders.")
public record EmployeeResolvedLabels(
        @Schema(nullable = true) DepartmentDto department,
        @Schema(nullable = true) SectionDto section,
        @Schema(nullable = true) DesignationDto designation,
        @Schema(nullable = true) GradeDto grade,
        @Schema(nullable = true) CityDto city,
        @Schema(nullable = true) ManagerSummary manager
) {}
