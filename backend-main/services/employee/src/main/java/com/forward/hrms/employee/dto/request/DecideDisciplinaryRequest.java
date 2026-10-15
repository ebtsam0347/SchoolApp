package com.forward.hrms.employee.dto.request;

import com.forward.hrms.employee.enums.DisciplinaryDecision;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

@Schema(name = "DecideDisciplinaryRequest",
        description = "Close out an ISSUED disciplinary action. UPHELD rows count toward warning totals; WITHDRAWN rows do not.")
public record DecideDisciplinaryRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull DisciplinaryDecision decision,
        @Schema(nullable = true) String decisionNotes,
        @Schema(description = "Defaults to today when omitted.", nullable = true) LocalDate closedOn
) {}
