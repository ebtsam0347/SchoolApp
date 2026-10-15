package com.forward.hrms.commons.approval.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(name = "UpdateDelegationRequest", description = "Extend / shorten / annotate. Null = unchanged.")
public record UpdateDelegationRequest(
        @Schema(nullable = true) LocalDate toDate,
        @Schema(nullable = true) String reason
) {}
