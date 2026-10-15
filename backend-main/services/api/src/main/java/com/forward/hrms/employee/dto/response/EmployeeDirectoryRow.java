package com.forward.hrms.employee.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "EmployeeDirectoryRow",
        description = "One row in the directory list — employee record + the catalog labels HR's table renders.")
public record EmployeeDirectoryRow(
        EmployeeDto employee,
        @Schema(description = "Resolved id→label for department, section, designation, grade, city + manager.")
        EmployeeResolvedLabels resolved
) {}
