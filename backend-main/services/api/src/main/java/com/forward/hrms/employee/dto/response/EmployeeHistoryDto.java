package com.forward.hrms.employee.dto.response;

import com.forward.hrms.employee.enums.HistoryChangeReason;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(name = "EmployeeHistoryDto", description = "One row of the employee's org-assignment history.")
public record EmployeeHistoryDto(
        long id,
        long employeeId,
        LocalDate effectiveFrom,
        @Schema(description = "Null on the current row.", nullable = true) LocalDate effectiveTo,
        @Schema(nullable = true) Long departmentId,
        @Schema(nullable = true) Long sectionId,
        @Schema(nullable = true) Long designationId,
        @Schema(nullable = true) Long gradeId,
        @Schema(nullable = true) Long managerId,
        @Schema(nullable = true) HistoryChangeReason changeReason,
        @Schema(nullable = true) String notes
) {}
