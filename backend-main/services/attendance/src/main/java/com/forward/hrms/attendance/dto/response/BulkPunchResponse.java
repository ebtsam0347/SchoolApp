package com.forward.hrms.attendance.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Outcome of a bulk operator-punch insert. {@code succeeded[]} carries the
 * rows actually written; {@code failed[]} carries the input row plus the
 * error code / message so the operator can fix and resubmit without
 * digging through logs. Mirrors {@link BulkOvertimeResponse}.
 */
@Schema(name = "BulkPunchResponse",
        description = "Per-row outcome of a bulk operator-punch insert. Either list may be empty.")
public record BulkPunchResponse(
        List<PunchDto> succeeded,
        List<FailedRow> failed
) {

    @Schema(name = "BulkPunchResponse.FailedRow",
            description = "An input row that failed to insert. {@code errorCode} is the stable " +
                    "{@code AttendanceErrorCode} name when the cause is a known business error.")
    public record FailedRow(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long employeeId,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDateTime punchedAt,
            String reason,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String errorCode,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String message
    ) {}
}
