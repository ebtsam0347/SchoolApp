package com.forward.hrms.attendance.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "DeviceReconciliationSummaryDto",
        description = "Counts of a completed snapshot's device users by reconciliation category. " +
                "Lets the FE show the breakdown without fetching the full per-user list.")
public record DeviceReconciliationSummaryDto(
        long deviceId,
        long snapshotId,
        int total,
        @Schema(description = "Matched to an ACTIVE employee — keep.")
        int matchedActive,
        @Schema(description = "Matched to a non-ACTIVE (e.g. TERMINATED) employee — un-enroll candidate.")
        int matchedInactive,
        @Schema(description = "No matching employee_code — stray enrollment, un-enroll candidate.")
        int unmatched
) {}
