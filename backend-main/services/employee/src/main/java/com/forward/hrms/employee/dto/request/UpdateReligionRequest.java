package com.forward.hrms.employee.dto.request;

import jakarta.validation.constraints.Size;

public record UpdateReligionRequest(

        @Size(max = 100, message = "name must not exceed 100 characters")
        String name,

        Boolean active

) {
}