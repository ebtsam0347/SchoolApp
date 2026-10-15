package com.forward.hrms.employee.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Size;

@Schema(name = "AssignRequisitionDetailEmployeeRequest",
        description = "Identifiers for the person hired against one approved requisition position. " +
                "Send either field or both; a null field is left unchanged.")
public record AssignRequisitionDetailEmployeeRequest(
        @Schema(description = "Employee code assigned once the hire is on the books.", maxLength = 64)
        @Size(max = 64) String newEmployeeCode,

        @Schema(description = "Temporary reference used until an employee code exists.", maxLength = 64)
        @Size(max = 64) String temporaryEmployeeReference
) {}
