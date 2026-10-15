package com.forward.hrms.employee.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BelongingMasterUpdateRequest(
        @NotBlank
        @Size(max = 100)
        String belongingName,

        @NotNull
        Long authorizedEmployeeId,

        @NotNull
        Boolean active
) {
}
