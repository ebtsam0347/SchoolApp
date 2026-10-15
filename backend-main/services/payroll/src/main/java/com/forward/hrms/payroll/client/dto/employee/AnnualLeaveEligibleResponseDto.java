package com.forward.hrms.payroll.client.dto.employee;

import java.time.LocalDate;

public record AnnualLeaveEligibleResponseDto(
        Long employeeId,
        String employeeCode,
        LocalDate joiningDate,
        String employmentStatus
) {
}
