package com.forward.hrms.commons.approval.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.Set;

@Schema(name = "CreateDelegationOnBehalfRequest",
        description = "HR (with APPROVAL_OVERRIDE) creates a delegation on a manager's behalf when the " +
                "manager is unreachable. `reason` is required for audit.")
public record CreateDelegationOnBehalfRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long delegatorId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long delegateId,
        @Schema(nullable = true, description = "Request type keys. Null = all types.") Set<String> requestTypes,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull LocalDate fromDate,
        @Schema(nullable = true) LocalDate toDate,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String reason
) {}
