package com.forward.hrms.attendance.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "UnenrollDeviceUsersRequest",
        description = "Bulk remove enrolled users from a device. Each entry queues a DELETE_USER " +
                "for the poller and drops the bridge row (if any). Provide matched employees by id " +
                "and/or stray device users by their raw device user_id. At least one list must be " +
                "non-empty.")
public record UnenrollDeviceUsersRequest(
        @Schema(description = "Matched employees to un-enroll. Their bridge row is dropped and a " +
                "DELETE_USER (keyed by employee_code) is queued.")
        List<Long> employeeIds,
        @Schema(description = "Raw device user_ids with no employee record (UNMATCHED rows). " +
                "A DELETE_USER keyed by the raw user_id is queued; there is no bridge row to drop.")
        List<String> deviceUserIds
) {}
