package com.forward.hrms.notification.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "UnreadCountResponse", description = "Bell-badge count.")
public record UnreadCountResponse(
        @Schema(description = "Number of unread notifications for the caller.", example = "3") long count
) {}
