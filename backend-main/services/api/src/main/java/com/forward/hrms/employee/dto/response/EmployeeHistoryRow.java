package com.forward.hrms.employee.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/**
 * One row of an employee's assignment history with catalog + manager
 * labels resolved server-side. Pairs the raw {@link EmployeeHistoryDto}
 * with denormalized labels so the page can render without a fan-out.
 */
@Schema(name = "EmployeeHistoryRow",
        description = "Point-in-time assignment row + resolved catalog labels and manager summary.")
public record EmployeeHistoryRow(
        @Schema(description = "history row id") long id,
        @Schema(description = "Inclusive start of this assignment.") LocalDate effectiveFrom,
        @Schema(description = "Inclusive end. Null when this is the current row.", nullable = true)
        LocalDate effectiveTo,
        @Schema(description = "ONBOARD / TRANSFER / PROMOTION / DEMOTION / DEPARTMENT_CHANGE / " +
                "MANAGER_CHANGE / SECTION_CHANGE / EXIT.", nullable = true)
        String changeReason,
        @Schema(description = "Free-text note captured at the time of change.", nullable = true)
        String notes,
        @Schema(nullable = true) DepartmentDto department,
        @Schema(nullable = true) SectionDto section,
        @Schema(nullable = true) DesignationDto designation,
        @Schema(nullable = true) GradeDto grade,
        @Schema(nullable = true) ManagerSummary manager,
        @Schema(description = "Raw upstream row, in case the FE wants the unresolved ids.")
        EmployeeHistoryDto raw
) {}
