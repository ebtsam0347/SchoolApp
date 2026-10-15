package com.forward.hrms.employee.dto.request;

import com.forward.hrms.employee.enums.EmployeeCategory;
import com.forward.hrms.employee.enums.EmployeeGender;
import com.forward.hrms.employee.enums.EmploymentType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

@Schema(name = "EmployeeRequisitionReplacementRequestDetail", description = "Details of the requisition, including the employee being replaced.")
public record EmployeeRequisitionReplacementRequestDetail(

        @Schema(description = "Required for REPLACEMENT — the employee being replaced.", example = "123")
        @NotNull
        Long replacingEmployeeId,

        @Enumerated(EnumType.STRING)
        @Schema(description = "Employment type for the open position. Possible values: PERMANENT, CONTRACT, INTERN")
        @NotNull
        EmploymentType employmentType,

        @Enumerated(EnumType.STRING)
        @Schema(description = "Employee category for the open position. Possible values: PERMANENT, CONTRACT, INTERN")
        @NotNull
        EmployeeCategory employeeCategory,

        @Schema(description = "Target date by which the position should be filled.", example = "2024-12-31")
        @NotNull
        LocalDate requiredDate,

        @Schema(description = "Name of the requester.", example = "John Doe")
        String yourName,

        @Enumerated(EnumType.STRING)
        @Schema(description = "Employee gender for the open position. Possible values: MALE, FEMALE, OTHER")
        @NotNull
        EmployeeGender employeeGender,

        @Schema(description = "Shift ID for the open position. Must be a valid shift in the system.", example = "1")
        @NotNull
        Long shiftId
) {
}
