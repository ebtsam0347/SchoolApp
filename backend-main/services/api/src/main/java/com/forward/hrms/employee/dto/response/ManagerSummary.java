package com.forward.hrms.employee.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ManagerSummary",
        description = "Lean view of an employee's direct manager — enough to render the manager strip.")
public record ManagerSummary(
        long id,
        String fullName,
        String employeeCode,
        @Schema(nullable = true) String profilePictureUrl
) {

    public static ManagerSummary from(EmployeeDto m) {
        if (m == null) return null;
        return new ManagerSummary(m.id(), m.fullName(), m.employeeCode(), m.profilePictureUrl());
    }
}
