package com.forward.hrms.notification.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "MarkReadResponse")
public record MarkReadResponse(
        @Schema(description = "Number of rows that transitioned from unread to read.") int marked
) {}
