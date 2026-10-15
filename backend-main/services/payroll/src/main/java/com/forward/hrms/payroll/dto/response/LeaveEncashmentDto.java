package com.forward.hrms.payroll.dto.response;

import com.forward.hrms.payroll.entity.LeaveEncashment;
import com.forward.hrms.payroll.enums.LeaveEncashmentStatus;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;

@Schema(name = "LeaveEncashmentDto")
public record LeaveEncashmentDto(
        long id,
        long employeeId,
        LocalDate calculationMonth,
        int periodInMonths,
        BigDecimal daysEncashed,
        @Schema(description = "Snapshot basic at calc time, rupees.") BigDecimal basicSalary,
        @Schema(description = "Computed amount, rupees.") BigDecimal amount,
        LeaveEncashmentStatus paymentStatus,
        @Schema(nullable = true) LocalDateTime paidAt,
        @Schema(nullable = true) Long payrollRunId,
        @Schema(nullable = true) String description,
        LocalDateTime createdAt
) {
    public static LeaveEncashmentDto of(LeaveEncashment e) {
        return new LeaveEncashmentDto(
                e.getId(), e.getEmployeeId(),
                e.getCalculationMonth(), e.getPeriodInMonths(),
                e.getDaysEncashed(), e.getBasicSalary(), e.getAmount(),
                e.getPaymentStatus(), e.getPaidAt(), e.getPayrollRunId(),
                e.getDescription(), e.getCreatedAt());
    }
}
