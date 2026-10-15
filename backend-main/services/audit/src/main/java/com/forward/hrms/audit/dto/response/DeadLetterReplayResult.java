package com.forward.hrms.audit.dto.response;

import com.forward.hrms.audit.enums.SkipReason;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Outcome of replaying a single dead-letter row.")
public record DeadLetterReplayResult(
        @Schema(description = "Status: INSERTED (row written + dead-letter deleted), DUPLICATE "
                + "(audit_events already had it; dead-letter deleted), STILL_FAILING "
                + "(ingest rejected again; dead-letter row updated with new reason).",
                example = "INSERTED")
        Status status,

        @Schema(description = "If STILL_FAILING, the new reason ingest returned. Else null.",
                nullable = true)
        SkipReason reason,

        @Schema(description = "If STILL_FAILING, free-form detail. Else null.", nullable = true)
        String detail
) {
    public enum Status { INSERTED, DUPLICATE, STILL_FAILING }
}
