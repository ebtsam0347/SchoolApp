package com.forward.hrms.payroll.dto.response;

import com.forward.hrms.payroll.entity.EmployeeFinalSettlement;
import com.forward.hrms.payroll.enums.EmployeeFinalSettlementStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record FinalSettlementDto(

        Long id,

        Long employeeId,

        String employeeCode,

        String employeeName,

        LocalDate lastWorkingDay,

        BigDecimal grossAmount,

        BigDecimal totalDeductions,

        BigDecimal netAmount,

        EmployeeFinalSettlementStatus status,

        LocalDate paymentDate,

        String remarks,

        LocalDateTime createdAt

) {
    public static FinalSettlementDto from(EmployeeFinalSettlement settlement) {
        return new FinalSettlementDto(
                settlement.getId(),
                settlement.getEmployeeId(),
                settlement.getEmployeeCode(),
                settlement.getEmployeeName(),
                settlement.getLastWorkingDay(),
                settlement.getGrossSettlement(),
                settlement.getTotalDeductions(),
                settlement.getNetSettlement(),
                settlement.getStatus(),
                //settlement.getPaymentDate(),
                null,
                settlement.getRemarks(),
                settlement.getCreatedAt()
        );
    }
}
