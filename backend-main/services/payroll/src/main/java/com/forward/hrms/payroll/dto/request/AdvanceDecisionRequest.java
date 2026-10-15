package com.forward.hrms.payroll.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Size;

@Schema(name = "AdvanceDecisionRequest", description = "Optional note on an approval / rejection action.")
public record AdvanceDecisionRequest(
        @Schema(nullable = true) @Size(max = 1024) String note
) {}
