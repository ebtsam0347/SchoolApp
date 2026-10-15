package com.forward.hrms.employee.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Picker row: ACTIVE employees by name + code, with the department label
 * resolved server-side. The service-neutral picker DTO every typeahead /
 * combobox surface uses (payroll off-cycle, attendance operator,
 * notification recipient, etc.).
 */
@Schema(name = "EmployeePickerCandidate",
        description = "Compact picker row: id, name, code, department, designation, section, photo.")
public record EmployeePickerCandidate(
        long id,
        String fullName,
        String employeeCode,
        @Schema(nullable = true) String departmentName,
        @Schema(nullable = true) String designationName,
        @Schema(nullable = true) String sectionName,
        @Schema(nullable = true, description = "Signed, short-lived avatar URL.") String profilePictureUrl
) {}
