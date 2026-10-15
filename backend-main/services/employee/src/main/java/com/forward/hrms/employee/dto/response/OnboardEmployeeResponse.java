package com.forward.hrms.employee.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "OnboardEmployeeResponse",
        description = "Response from POST /employees. Wraps the freshly-created employee plus, "
                + "when any cross-service work was requested (ESS provisioning, basic salary, "
                + "salary components, bank details, shift assignment), the id of the onboarding "
                + "saga the FE can poll via GET /sagas/{id} for live per-step status.")
public record OnboardEmployeeResponse(
        @Schema(description = "The created employee. Cross-service work runs out-of-band — the "
                + "saga's outcome is reflected on subsequent reads (e.g. essProvisioningStatus, "
                + "payroll, attendance lookups).")
        EmployeeDto employee,
        @Schema(description = "Saga row id, present when at least one cross-service section was "
                + "requested (provisionEss=true, salary, or shift). Use it on GET /sagas/{id} to "
                + "surface RUNNING / COMPLETED / FAILED per step in the UI.",
                nullable = true)
        Long sagaId
) {
    public static OnboardEmployeeResponse of(EmployeeDto employee, Long sagaId) {
        return new OnboardEmployeeResponse(employee, sagaId);
    }
}
