package com.forward.hrms.payroll.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@Schema(name = "UpdateIncrementBatchRequest", description = "DRAFT-only.")
public record UpdateIncrementBatchRequest(
        @Size(max = 128) String name,
        LocalDate effectiveFrom,
        String notes
) {}
