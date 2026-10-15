package com.forward.hrms.payroll.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(name = "UpsertBankDetailsRequest")
public record UpsertBankDetailsRequest(
        @Schema(description = "ID of a row in the banks catalog.", requiredMode = Schema.RequiredMode.REQUIRED)
        Long bankId,
        @Size(max = 128) String branch,
        @NotBlank @Size(max = 128) String accountTitle,

        // §7.payroll — Pakistan IBAN: PK + 2 check digits + 4-letter bank
        // code + 16 alphanumeric account = 24 chars total. Anchored
        // strict regex; uppercase-only.
        @Schema(description = "Pakistan IBAN: PK + 2 digits + 4-letter bank code + 16 alphanumeric (24 chars).")
        @NotBlank
        @Pattern(regexp = "^PK\\d{2}[A-Z]{4}[A-Z0-9]{16}$",
                message = "iban must be a 24-char Pakistan IBAN: PK<2 digits><4 letters><16 alphanumeric>")
        @Size(max = 34) String iban,

        @Size(max = 64) String accountNumber
) {}
