package com.forward.hrms.employee.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "TerminateRequest",
        description = "Immediate termination. Last working day = today; emits employee.terminated immediately.")
public record TerminateRequest(
        @Schema(description = "Short categorisation.", maxLength = 64,
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 64) String reason,

        @Schema(description = "Free-text notes for HR.") String notes,

        @Schema(description = "If the employee has direct reports, the id of the employee they " +
                "should be reassigned to. Required when reports exist.", nullable = true)
        Long reassignReportsTo
) {}
