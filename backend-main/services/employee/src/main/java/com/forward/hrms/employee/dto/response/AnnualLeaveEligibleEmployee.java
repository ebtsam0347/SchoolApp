package com.forward.hrms.employee.dto.response;

import com.forward.hrms.employee.enums.EmploymentStatus;

import java.time.LocalDate;

public record AnnualLeaveEligibleEmployee(
        Long employeeId,
        String employeeCode,
        LocalDate joiningDate,
        String employmentStatus
) {
}
