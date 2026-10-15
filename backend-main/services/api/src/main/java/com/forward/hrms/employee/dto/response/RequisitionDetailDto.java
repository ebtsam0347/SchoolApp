package com.forward.hrms.employee.dto.response;

import com.forward.hrms.employee.enums.EmployeeCategory;
import com.forward.hrms.employee.enums.EmployeeGender;
import com.forward.hrms.employee.enums.EmploymentType;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One row of {@code employee_requisition_detail} — a single requested position
 * under a requisition head. Catalog ids carry their resolved labels so the FE
 * doesn't have to fan out to the catalog endpoints per row.
 */
@Schema(name = "RequisitionDetailDto", description = "A single requested position under a requisition.")
public record RequisitionDetailDto(
        @Schema(description = "Detail row id.") long id,
        @Schema(description = "Owning requisition head id.") long requisitionId,

        @Schema(description = "Employee being replaced (REPLACEMENT only).", nullable = true) Long replacingEmployeeId,
        @Schema(description = "Employee code of the employee being replaced.", nullable = true) String replacingEmployeeCode,
        @Schema(description = "Full name of the employee being replaced.", nullable = true) String replacingEmployeeName,

        @Schema(description = "Employee code assigned once the position is filled.", nullable = true) String newEmployeeCode,
        @Schema(description = "Temporary reference used before an employee code exists.", nullable = true) String temporaryEmployeeReference,

        @Schema(nullable = true) Long sectionId,
        @Schema(nullable = true) String sectionCode,
        @Schema(nullable = true) String sectionName,

        @Schema(nullable = true) Long designationId,
        @Schema(nullable = true) String designationCode,
        @Schema(nullable = true) String designationTitle,

        @Schema(nullable = true) Long gradeId,
        @Schema(nullable = true) String gradeCode,
        @Schema(nullable = true) String gradeName,

        @Schema(description = "PERMANENT, CONTRACT or INTERN.", nullable = true) EmploymentType employmentType,
        @Schema(description = "DIRECT or INDIRECT.", nullable = true) EmployeeCategory employeeCategory,
        @Schema(description = "Date the position is required until (CONTRACT/INTERN).", nullable = true) LocalDate requiredUntil,
        @Schema(description = "MALE, FEMALE or ANY.", nullable = true) EmployeeGender employeeGender,
        @Schema(nullable = true) Long shiftId,
        @Schema(nullable = true) String shiftName,

        @Schema(description = "True when the position was struck off during lean review.") boolean leanRemoved,
        @Schema(nullable = true) String leanRemovedRemarks,
        @Schema(nullable = true) Long leanRemovedBy,

        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String createdBy,
        String updatedBy
) {}
