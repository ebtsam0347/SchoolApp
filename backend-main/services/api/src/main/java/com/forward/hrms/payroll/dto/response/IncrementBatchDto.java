package com.forward.hrms.payroll.dto.response;

import com.forward.hrms.payroll.enums.IncrementBatchStatus;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;

@Schema(name = "IncrementBatchDto")
public record IncrementBatchDto(
        long id,
        String name,
        IncrementBatchStatus status,
        LocalDate effectiveFrom,
        @Schema(nullable = true) String sourceFilename,
        @Schema(nullable = true) String notes,
        @Schema(nullable = true) LocalDateTime appliedAt,
        @Schema(nullable = true) Long appliedBy,
        @Schema(nullable = true) List<IncrementBatchLineDto> lines
) {}
