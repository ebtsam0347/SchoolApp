package com.forward.hrms.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(name = "IssueServiceTokenRequest",
        description = "Mint a long-lived JWT for a service-account user. Returned once, never stored. " +
                "Rotation is admin-driven: re-issue, re-seal, restart the consumer.")
public record IssueServiceTokenRequest(
        @Schema(description = "Token lifetime in days. Cap is 730 (~2y) to keep `exp` as the one revocation lever " +
                "we have until a denylist exists.",
                example = "365",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        @Min(1)
        @Max(730)
        Integer ttlDays
) {}
