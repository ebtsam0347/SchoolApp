package com.forward.hrms.payroll.client.dto.attendace;
import java.math.BigDecimal;
import java.time.LocalDate;

public record AnnualLeaveCalcBalanceResponseDto (

        long id,
        long employeeId,
        long leaveTypeId,
        int year,
        BigDecimal allocated,
        BigDecimal used,
        BigDecimal pending,
        BigDecimal carried,
        BigDecimal available,
        LocalDate cycleStart,
        LocalDate cycleEnd
){
}
