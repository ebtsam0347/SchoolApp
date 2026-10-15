package com.forward.hrms.employee.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ReligionRequest(
        @NotBlank(message = "Religion name is required")
        String name,
        Boolean active
) {
}