package com.forward.hrms.payroll.dto.response;

import com.forward.hrms.employee.dto.response.EmployeeSummary;
import com.forward.hrms.payroll.enums.BonusTransactionPaymentMode;
import com.forward.hrms.payroll.enums.BonusTransactionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(name = "BonusTransactionDto")
public record BonusTransactionDto(
        long id,
        String fiscalYearCode,
        int bonusNo,
        long employeeId,
        String employeeCode,
        EmployeeSummary myEmployee,
        LocalDate dateOfJoining,
        BigDecimal basicSalary,
        BigDecimal percentageOfSalary,
        BigDecimal percentageSalaryAmount,
        long noOfDays,
        BigDecimal deductionAsLongTermAdvance,
        BigDecimal rfdAdvanceAdded,
        BigDecimal netAmount,
        BonusTransactionPaymentMode paymentMode,
        BonusTransactionStatus status,
        @Schema(nullable = true) LocalDateTime paidAt
) {}
