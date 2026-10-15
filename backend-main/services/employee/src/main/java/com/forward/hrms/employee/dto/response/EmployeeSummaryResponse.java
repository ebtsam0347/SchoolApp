package com.forward.hrms.employee.dto.response;

public record EmployeeSummaryResponse(
        Long id,
        String employeeCode,
        String employeeName,
        String department,
        String section,
        String grade
) {
}
