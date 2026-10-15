package com.forward.hrms.employee.dto.request;

import jakarta.validation.constraints.Size;

public record UpdateOffCyclePaymentType(

        @Size(max = 100)
        String name,

        @Size(max = 500)
        String description,

        Boolean active

) {
}