package com.forward.hrms.payroll.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Size;

@Schema(name = "UpdateBankRequest", description = "Patch a bank catalog row. Null fields are unchanged. `code` is immutable.")
public record UpdateBankRequest(
        @Schema(nullable = true) @Size(min = 1, max = 128) String name,
        @Schema(nullable = true) @Size(max = 128) String branch,
        @Schema(nullable = true) @Size(max = 255) String address,
        @Schema(nullable = true) @Size(max = 128) String accountTitle,
        @Schema(nullable = true) @Size(max = 34) String iban,
        @Schema(nullable = true) @Size(max = 64) String accountNumber,
        @Schema(nullable = true) Boolean active
) {}
