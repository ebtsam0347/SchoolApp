package com.forward.hrms.commons.approval.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Schema(name = "DelegationDto", description = "An approval delegation record.")
public record DelegationDto(
        long id,
        Long delegatorId,
        Long delegateId,
        @Schema(nullable = true, description = "Request type keys covered. Null = all types.") List<String> requestTypes,
        LocalDate fromDate,
        @Schema(nullable = true) LocalDate toDate,
        @Schema(nullable = true) String reason,
        @Schema(description = "ACTIVE or REVOKED.") String status,
        @Schema(nullable = true) LocalDateTime revokedAt,
        @Schema(nullable = true) Long revokedBy
) {}
