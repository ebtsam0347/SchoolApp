package com.forward.hrms.employee.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Size;

@Schema(name = "UpdateEmergencyContactRequest", description = "Patch. Null fields are left unchanged.")
public record UpdateEmergencyContactRequest(
        @Size(max = 128) String name,
        @Size(max = 64) String relation,
        @Size(max = 32) String phone,
        Boolean primary
) {}
