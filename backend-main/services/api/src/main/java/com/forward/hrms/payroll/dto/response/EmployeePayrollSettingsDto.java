package com.forward.hrms.payroll.dto.response;

import com.forward.hrms.payroll.enums.PaymentMode;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(name = "EmployeePayrollSettingsDto")
public record EmployeePayrollSettingsDto(
        long id,
        long employeeId,
        LocalDate hireDate,
        boolean eobiEnrolled,
        PaymentMode paymentMode,
        @Schema(nullable = true,
                description = "When gratuity accrual began. Falls back to hireDate when null.")
        LocalDate gratuityStartDate,
        @Schema(nullable = true,
                description = "When senior-allowance eligibility began. Null = ineligible.")
        LocalDate seniorAllowanceStartDate
) {}
