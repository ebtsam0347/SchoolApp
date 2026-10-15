package com.forward.hrms.commons.approval.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.Set;

@Schema(name = "CreateDelegationRequest",
        description = "Create or extend an approval delegation. `requestTypes` null = cover all known types. " +
                "`toDate` null = open-ended (until revoked).")
public record CreateDelegationRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long delegateId,
        @Schema(nullable = true, description = "Request type keys, e.g. LEAVE, OVERTIME, REGULARIZATION, SHORT_LEAVE. " +
                "Null = all types.") Set<String> requestTypes,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalDate fromDate,
        @Schema(nullable = true) LocalDate toDate,
        @Schema(nullable = true) String reason
) {}
