package com.forward.hrms.notification.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

@Schema(name = "BulkMarkReadRequest",
        description = "Mark a set of notifications read in one call. Ids that don't belong to the caller "
                + "or that are already read are silently skipped.")
public record BulkMarkReadRequest(
        @Schema(description = "Notification ids to mark read.",
                requiredMode = Schema.RequiredMode.REQUIRED, example = "[10421, 10422, 10423]")
        @NotEmpty List<Long> ids
) {}
