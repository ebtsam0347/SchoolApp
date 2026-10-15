package com.forward.hrms.employee.dto.response;

import com.forward.hrms.employee.enums.EmploymentStatus;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "EmployeeStatusDto", description = "Employee id with its current employment status.")
public record EmployeeStatusDto(
        @Schema(description = "Internal id.", example = "42") long id,
        @Schema(description = "Current employment status.", example = "ACTIVE") EmploymentStatus employmentStatus
) {}
