package com.forward.hrms.payroll.dto.request;

import com.forward.hrms.payroll.enums.PaymentMode;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(name = "UpdateEmployeePayrollSettingsRequest", description = "Null fields are left unchanged.")
public record UpdateEmployeePayrollSettingsRequest(
        Boolean eobiEnrolled,
        PaymentMode paymentMode,
        @Schema(nullable = true,
                description = "When gratuity accrual began. Usually equals hireDate but can differ — set " +
                        "explicitly when prior service is recognised. Locked once set.")
        LocalDate gratuityStartDate,
        @Schema(nullable = true,
                description = "When senior-allowance eligibility began. Null = ineligible. Set non-null " +
                        "to start accruing the seniority-tied allowance from that date.")
        LocalDate seniorAllowanceStartDate
) {}
