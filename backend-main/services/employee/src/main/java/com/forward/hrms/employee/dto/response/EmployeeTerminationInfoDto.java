package com.forward.hrms.employee.dto.response;

import com.forward.hrms.employee.enums.GradeType;

import java.time.LocalDate;

public record EmployeeTerminationInfoDto(

        Long employeeId,

        String employeeCode,

        String employeeName,

        String department,

        String section,

        String designation,

        String grade,

        GradeType gradeType,

        LocalDate joiningDate
) {
}
