package com.forward.hrms.employee.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@Schema(name = "UpdatePriorExperienceRequest", description = "Patch. Null fields are left unchanged.")
public record UpdatePriorExperienceRequest(
        @Size(max = 255) String employerName,
        @Size(max = 255) String designation,
        LocalDate startDate,
        LocalDate endDate,
        String responsibilities,
        @Size(max = 255) String reasonForLeaving
) {}
