package com.forward.hrms.employee.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AssignmentChangeRequest",
        description = "Body shared by /employees/{id}/manager, /department, /section, /designation, /grade. " +
                "Pass `targetId=0` to clear the assignment (where allowed).")
public record AssignmentChangeRequest(
        @Schema(description = "Catalog row id to assign.", example = "3") Long targetId,
        @Schema(description = "Optional notes recorded on employee_history.") String notes
) {}
