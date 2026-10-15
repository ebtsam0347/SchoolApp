package com.forward.hrms.payroll.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AllowanceEmployeeDto", description = "Display fields of the employee an allowance set belongs to.")
public record AllowanceEmployeeDto(
        @Schema(description = "Employee id.") long id,
        @Schema(description = "Full name.") String fullName,
        @Schema(description = "Card no.", nullable = true) String employeeCode,
        @Schema(nullable = true) String departmentName,
        @Schema(nullable = true) String sectionName,
        @Schema(nullable = true) String designationName,
        @Schema(nullable = true) String profilePictureUrl
) {}
