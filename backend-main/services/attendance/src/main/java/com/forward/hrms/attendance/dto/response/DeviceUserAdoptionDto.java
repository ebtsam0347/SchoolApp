package com.forward.hrms.attendance.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "DeviceUserAdoptionDto",
        description = "Outcome of adopting a completed device snapshot's user dump into the " +
                "attendance_device_users bridge. Matches each device user_id (leading zeros " +
                "stripped) to an employee_code; creates or updates the bridge row and skips users " +
                "with no matching employee. hasFingerprint / hasFace are NOT inferred here — " +
                "get_users() can't report template presence, so they stay at their current value " +
                "(false on newly created rows).")
public record DeviceUserAdoptionDto(
        long deviceId,
        long snapshotId,
        @Schema(description = "Total user entries present in the snapshot dump.")
        int totalUsers,
        @Schema(description = "Bridge rows newly created.")
        int created,
        @Schema(description = "Existing bridge rows updated (privilege / syncedAt refreshed).")
        int updated,
        @Schema(description = "Device users with no matching employee_code — skipped, not adopted.")
        int skipped,
        @Schema(description = "The device user_id values (as reported, before zero-strip) that " +
                "matched no employee. Surfaced so HR can reconcile stray enrollments.")
        List<String> unmatchedUserIds
) {}
