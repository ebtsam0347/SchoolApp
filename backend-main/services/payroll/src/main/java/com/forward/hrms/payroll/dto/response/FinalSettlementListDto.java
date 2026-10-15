package com.forward.hrms.payroll.dto.response;


import com.forward.hrms.employee.enums.GradeType;
import com.forward.hrms.payroll.enums.EmployeeFinalSettlementStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Builder
public record FinalSettlementListDto(

        Long settlementId,

        Long employeeId,

        String employeeCode,

        String employeeName,

        String department,

        String section,

        String designation,

        String grade,

        GradeType gradeType,

        LocalDate hireDate,

        LocalDate lastWorkingDay,

        EmployeeFinalSettlementStatus status,

        BigDecimal grossSettlement,

        BigDecimal totalDeductions,

        BigDecimal netSettlement,

        String remarks,

        LocalDateTime createdAt

) {
}
