package com.forward.hrms.payroll.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@Schema(name = "UpdateBonusEventRequest", description = "DRAFT-only. Null fields are left unchanged.")
public record UpdateBonusEventRequest(
        @Size(max = 128) String name,
        LocalDate referenceDate,
        @Positive Integer fullServiceDaysThreshold,
        String notes
) {}
