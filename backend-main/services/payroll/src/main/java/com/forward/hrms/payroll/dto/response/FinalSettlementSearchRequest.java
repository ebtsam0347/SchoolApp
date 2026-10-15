package com.forward.hrms.payroll.dto.response;

import com.forward.hrms.employee.enums.GradeType;
import com.forward.hrms.payroll.enums.EmployeeFinalSettlementStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
public class FinalSettlementSearchRequest {

    private Long employeeId;

    private String employeeCode;

    private String employeeName;

    private EmployeeFinalSettlementStatus status;

    private String department;

    private String section;

    private String designation;

    private String grade;

    private GradeType gradeType;

    /*
     * Employee Hire Date
     */
    private LocalDate hireDateFrom;

    private LocalDate hireDateTo;

    /*
     * Last Working Day
     */
    private LocalDate lastWorkingDayFrom;

    private LocalDate lastWorkingDayTo;

    /*
     * Settlement Creation Date
     */
    private LocalDateTime createdFrom;

    private LocalDateTime createdTo;
}
