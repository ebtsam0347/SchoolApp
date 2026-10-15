package com.forward.hrms.employee.dto.response;

import com.forward.hrms.employee.entity.Disability;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "DisabilityDto", description = "Disability category catalog. Used for compliance reporting under " +
        "the Persons with Disabilities (Employment & Rehabilitation) ordinance.")
public record DisabilityDto(
        long id,
        String code,
        String name,
        boolean active
) {
    public static DisabilityDto of(Disability d) {
        return new DisabilityDto(d.getId(), d.getCode(), d.getName(), d.isActive());
    }
}
