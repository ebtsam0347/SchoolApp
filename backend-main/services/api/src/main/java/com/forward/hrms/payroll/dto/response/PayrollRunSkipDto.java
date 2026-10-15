package com.forward.hrms.payroll.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(name = "PayrollRunSkipDto",
        description = "An employee that compute could not produce a payslip for, with the actionable reason. " +
                "FE drives the fix-and-retry workflow off these rows.")
public record PayrollRunSkipDto(
        @Schema(description = "Skip-row id (stable across paginated reads).") long id,
        long employeeId,
        String employeeCode,
        String employeeFullName,
        @Schema(description = "Stable error code; FE maps to a deeplink (e.g. NO_EFFECTIVE_SALARY → salary page).")
        String errorCode,
        @Schema(description = "Human-readable reason from the compute step.") String message,
        LocalDateTime createdAt
) {}
