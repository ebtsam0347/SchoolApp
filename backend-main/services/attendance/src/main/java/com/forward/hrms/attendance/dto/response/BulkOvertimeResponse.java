package com.forward.hrms.attendance.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

@Schema(name = "BulkOvertimeResponse",
        description = "Outcome of a bulk OT submission. `created[]` lists the rows actually written; " +
                "`skipped[]` lists employee ids that were silently dropped because they had no punch on the date.")
public record BulkOvertimeResponse(
        UUID bulkBatchId,
        List<OvertimeRequestDto> created,
        List<Long> skipped
) {}
