package com.forward.hrms.payroll.client.dto.employee;

import com.forward.hrms.employee.enums.GradeType;

import java.time.LocalDate;

public record EmployeeTerminationInfo(

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

    public static EmployeeTerminationInfo placeholder(Long employeeId) {

        return new EmployeeTerminationInfo(

                employeeId,

                "EMP-" + employeeId,

                "Unknown Employee",

                null,

                null,

                null,

                null,

                null,

                null
        );
    }
}

