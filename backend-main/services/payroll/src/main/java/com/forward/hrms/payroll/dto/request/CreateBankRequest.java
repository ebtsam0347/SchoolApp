package com.forward.hrms.payroll.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "CreateBankRequest",
        description = "HR/Accounts create payload for the bank catalog. The same row carries the company's " +
                "own account at this bank — payroll runs read iban / accountNumber as the disbursement " +
                "source. `code` must be unique and is immutable post-create.")
public record CreateBankRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "MCB")
        @NotBlank @Size(min = 1, max = 32) String code,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "MCB Bank Limited")
        @NotBlank @Size(min = 1, max = 128) String name,

        @Schema(nullable = true) @Size(max = 128) String branch,
        @Schema(nullable = true) @Size(max = 255) String address,

        @Schema(nullable = true,
                description = "Title of the company's own account at this bank.") @Size(max = 128) String accountTitle,
        @Schema(nullable = true, description = "Pakistan IBAN, 24 chars.") @Size(max = 34) String iban,
        @Schema(nullable = true) @Size(max = 64) String accountNumber
) {}
