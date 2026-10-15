package com.forward.hrms.payroll.dto.response;

import com.forward.hrms.employee.dto.response.EmployeeSummary;
import com.forward.hrms.payroll.entity.BonusTransaction;

public final class BonusTransactionDtoMapper {

    private BonusTransactionDtoMapper() {}

    public static BonusTransactionDto of(BonusTransaction t, EmployeeSummary employee) {
        return new BonusTransactionDto(
                t.getId(),
                t.getFiscalYearCode(),
                t.getBonusNo(),
                t.getEmployeeId(),
                t.getEmployeeCode(),
                employee,
                t.getDateOfJoining(),
                t.getBasicSalary(),
                t.getPercentageOfSalary(),
                t.getPercentageSalaryAmount(),
                t.getNoOfDays(),
                t.getDeductionAsLongTermAdvance(),
                t.getRfdAdvanceAdded(),
                t.getNetAmount(),
                t.getBonusTransactionPaymentMode(),
                t.getStatus(),
                t.getPaidAt()
        );
    }
}
