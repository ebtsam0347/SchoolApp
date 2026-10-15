package com.forward.hrms.employee.dto.request;

import com.forward.hrms.employee.enums.DisabilitySeverity;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(name = "AddEmployeeDisabilityRequest",
        description = "Link an employee to a disability category. (employeeId, disabilityId) is unique.")
public record AddEmployeeDisabilityRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "FK into disabilities.id.")
        @NotNull Long disabilityId,

        @Schema(nullable = true, example = "Right arm polio",
                description = "Free-text description for compliance reporting. Use this for the " +
                        "specific impairment phrase (legacy WebHRMS stored 78 distinct values).")
        @Size(max = 255) String description,

        @Schema(nullable = true) DisabilitySeverity severity,

        @Schema(nullable = true, description = "Object key in the employee-docs bucket for the supporting medical certificate.")
        @Size(max = 512) String documentKey
) {}
