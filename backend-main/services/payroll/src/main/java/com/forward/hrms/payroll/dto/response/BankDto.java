package com.forward.hrms.payroll.dto.response;

import com.forward.hrms.payroll.entity.Bank;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "BankDto",
        description = "Bank catalog row. Carries the company's own account at this bank " +
                "(payroll disbursement source) and serves as the controlled list employees pick from.")
public record BankDto(
        long id,
        String code,
        String name,
        @Schema(nullable = true) String branch,
        @Schema(nullable = true) String address,
        @Schema(nullable = true, description = "Title of the company's account at this bank.") String accountTitle,
        @Schema(nullable = true) String iban,
        @Schema(nullable = true) String accountNumber,
        boolean active
) {
    public static BankDto of(Bank b) {
        return new BankDto(b.getId(), b.getCode(), b.getName(), b.getBranch(), b.getAddress(),
                b.getAccountTitle(), b.getIban(), b.getAccountNumber(), b.isActive());
    }
}
