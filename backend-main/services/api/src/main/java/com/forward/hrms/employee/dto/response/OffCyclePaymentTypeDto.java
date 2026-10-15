package com.forward.hrms.employee.dto.response;

public record OffCyclePaymentTypeDto(

        Long id,
        String name,
        String description,
        Boolean active

) {
}