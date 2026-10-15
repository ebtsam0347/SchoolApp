package com.forward.hrms.employee.dto.response;

import com.forward.hrms.employee.enums.DisciplinaryLevel;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

@Schema(name = "DisciplinaryActionListResponse",
        description = "Per-employee disciplinary list plus computed UPHELD-only warning counts " +
                "per level for the requested fiscal year.")
public record DisciplinaryActionListResponse(
        @Schema(description = "Disciplinary rows, newest first.") List<DisciplinaryActionDto> actions,

        @Schema(description = "Fiscal year code (the calendar year the FY ends in — e.g. 2026 for FY 2025-26).",
                example = "2026")
        int fyCode,

        @Schema(description = "UPHELD-only count per level inside the requested FY window.")
        Map<DisciplinaryLevel, Long> warningCounts
) {}
