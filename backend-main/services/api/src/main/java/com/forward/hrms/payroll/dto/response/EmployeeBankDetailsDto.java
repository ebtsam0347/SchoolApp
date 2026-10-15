package com.forward.hrms.payroll.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "EmployeeBankDetailsDto")
public record EmployeeBankDetailsDto(
        long id,
        long employeeId,
        long bankId,
        @Schema(nullable = true, description = "Catalog code (denormalized).") String bankCode,
        @Schema(nullable = true, description = "Catalog display name (denormalized).") String bankName,
        @Schema(nullable = true) String branch,
        String accountTitle,
        String iban,
        @Schema(description = "Encrypted at rest. Returned only to HR / payroll roles.",
                nullable = true) String accountNumber
) {}
