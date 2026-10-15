package com.forward.hrms.employee.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Size;

@Schema(name = "UpdateEmployeeQualificationRequest", description = "Patch. Null fields are left unchanged.")
public record UpdateEmployeeQualificationRequest(
        @Size(max = 255) String institution,
        @Size(max = 255) String boardOrUniversity,
        Integer yearOfCompletion,
        @Size(max = 32) String grade,
        @Size(max = 512) String documentKey
) {}
