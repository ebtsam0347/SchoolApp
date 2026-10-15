package com.forward.hrms.attendance.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "UnenrollResultDto",
        description = "Outcome of a bulk un-enroll. DELETE_USER tasks drain on the next poller " +
                "cycle; offline devices drain when they return.")
public record UnenrollResultDto(
        @Schema(description = "DELETE_USER tasks queued.")
        int queued,
        @Schema(description = "Bridge rows dropped (matched employees that were enrolled).")
        int droppedBridges,
        @Schema(description = "Entries skipped (unknown employee id, or blank device user_id).")
        int skipped
) {}
