package com.forward.hrms.attendance.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ApproveRequest", description = "Optional note attached to the approval step.")
public record ApproveRequest(
        @Schema(nullable = true) String note
) {}
