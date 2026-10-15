package com.forward.hrms.employee.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OffCyclePaymentTypeRequest(

        @NotBlank(message = "name is required")
        @Size(max = 100)
        String name,

        @Size(max = 500)
        String description,

        Boolean active

) {
}