package com.forward.hrms.notification.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AnnounceResponse")
public record AnnounceResponse(
        @Schema(description = "Number of notifications inserted.") int delivered
) {}
