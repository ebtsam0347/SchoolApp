package com.forward.hrms.attendance.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;

@Schema(name = "DelegationDto")
public record DelegationDto(
        long id,
        long delegatorId,
        long delegateId,
        @Schema(nullable = true) List<String> requestTypes,
        LocalDate fromDate,
        @Schema(nullable = true) LocalDate toDate,
        @Schema(nullable = true) String reason,
        String status,
        @Schema(nullable = true) LocalDateTime revokedAt,
        @Schema(nullable = true) Long revokedBy
) {}
