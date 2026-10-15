package com.forward.hrms.employee.dto.request;

import com.forward.hrms.employee.enums.EmploymentType;
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Min;

import java.time.LocalDate;

@Schema(name = "UpdateRequisitionRequest",
        description = "All fields are optional — only non-null values are applied. Requisition must be in DRAFT status.")
public record UpdateRequisitionRequest(
        @Schema(nullable = true) Long departmentId,
        @Schema(nullable = true) Long sectionId,
        @Schema(nullable = true) Long designationId,
        @Schema(nullable = true) Long gradeId,
        @Schema(nullable = true) EmploymentType employmentType,
        @Schema(nullable = true) @Min(1) Integer vacancies,
        @Schema(nullable = true) Long replacingEmployeeId,
        @Schema(nullable = true) String reason,
        @Schema(nullable = true) LocalDate requiredBy,
        @Schema(nullable = true) String notes
) {}