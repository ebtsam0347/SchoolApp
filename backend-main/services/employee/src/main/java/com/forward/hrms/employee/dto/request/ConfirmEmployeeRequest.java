package com.forward.hrms.employee.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ConfirmEmployeeRequest", description = "Confirm an employee out of probation.")
public record ConfirmEmployeeRequest(
        @Schema(description = "Optional confirmation notes recorded on the new permanent contract.")
        String notes
) {}
