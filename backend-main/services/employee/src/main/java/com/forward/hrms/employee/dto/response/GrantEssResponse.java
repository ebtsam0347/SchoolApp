package com.forward.hrms.employee.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "GrantEssResponse",
        description = "Response from POST /employees/{id}/grant-ess. Wraps the employee row and the "
                + "id of the saga that drives the auth-service call out-of-band. The FE polls "
                + "GET /sagas/{id} for live status (RUNNING / COMPLETED / FAILED).")
public record GrantEssResponse(
        @Schema(description = "The employee row at kickoff time. `userId` is patched onto the row "
                + "by the saga's PROVISION_ESS step; subsequent reads will reflect it.")
        EmployeeDto employee,
        @Schema(description = "Saga row id. Always present; the auth call always runs through the saga.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Long sagaId
) {
    public static GrantEssResponse of(EmployeeDto employee, Long sagaId) {
        return new GrantEssResponse(employee, sagaId);
    }
}
