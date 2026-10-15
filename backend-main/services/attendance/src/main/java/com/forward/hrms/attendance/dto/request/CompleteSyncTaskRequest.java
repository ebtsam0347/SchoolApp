package com.forward.hrms.attendance.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "CompleteSyncTaskRequest",
        description = "Reported by the poller when a sync task succeeded on the device.")
public record CompleteSyncTaskRequest(
        @Schema(nullable = true, description = "Free-text confirmation, e.g. firmware echo of the new user.")
        String detail
) {}
