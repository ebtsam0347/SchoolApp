package com.forward.hrms.employee.dto.response;

import com.forward.hrms.employee.enums.DisciplinaryCategory;
import com.forward.hrms.employee.enums.DisciplinaryDecision;
import com.forward.hrms.employee.enums.DisciplinaryLevel;
import com.forward.hrms.employee.enums.DisciplinaryStatus;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(name = "DisciplinaryActionDto", description = "One disciplinary-action row.")
public record DisciplinaryActionDto(
        long id,
        long employeeId,
        DisciplinaryLevel level,
        DisciplinaryCategory category,
        @Schema(nullable = true) LocalDate incidentDate,
        LocalDate issuedOn,
        String reason,
        @Schema(nullable = true) DisciplinaryDecision decision,
        @Schema(nullable = true) String decisionNotes,
        @Schema(nullable = true) LocalDate closedOn,
        DisciplinaryStatus status
) {}
