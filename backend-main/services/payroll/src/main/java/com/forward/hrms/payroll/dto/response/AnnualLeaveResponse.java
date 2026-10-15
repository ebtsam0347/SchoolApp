package com.forward.hrms.payroll.dto.response;

import com.forward.hrms.employee.dto.response.EmployeeSummary;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AnnualLeaveResponse(

        Long id,

        Long employeeId,

        EmployeeSummary employee,

        String employeeCode,

        LocalDate calculationMonth,

        BigDecimal basicSalary,

        BigDecimal perDaySalary,

        LocalDate cycleStart,

        LocalDate cycleEnd,

        Long eligibleLeaveDays,

        Long availedLeaveDays,

        Long payableLeaveDays,

        BigDecimal leaveAmount,

        BigDecimal grossAnnualLeaveAmount,

        BigDecimal deductionAsLongTermAdvance,

        BigDecimal netAmount,

        Boolean paymentStatus,

        Boolean lockStatus,

        LocalDate joiningDate

) {
}